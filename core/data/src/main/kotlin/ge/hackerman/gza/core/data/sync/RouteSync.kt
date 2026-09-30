package ge.hackerman.gza.core.data.sync

import android.util.Log
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.dao.RouteDataRows
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.data.database.routeDataRows
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Refreshes one route's patterns, stop order, shapes and timetables: everything is fetched
 * first, then written in one transaction, and nothing is written on any failure. A route with
 * two patterns costs 7 requests; language-neutral data is fetched once, in English.
 */
@Singleton
internal class RouteSync @Inject constructor(
    private val gateway: TtcGatewayClient,
    database: GzaDatabase,
    private val tracker: SyncStatusTracker,
    private val policy: StalenessPolicy,
    private val clock: Clock
) {
    private val dao = database.routeDataDao()
    private val syncStateDao = database.syncStateDao()
    private val locks = KeyedMutex<SyncKey>()

    // Shared by every route sync, so three routes opening at once still keep the gateway to
    // a few requests at a time (PLAN.md 3.2).
    private val requests = Semaphore(MAX_REQUESTS_IN_FLIGHT)

    /**
     * A screen uses [id]: records the use (at most once an hour), and refreshes when older than
     * 12 h or never synced.
     */
    suspend fun syncIfStale(id: RouteId): SyncOutcome = syncIfStale(id, markUsed = true)

    /** Refreshes now, whatever the age or the error backoff: the user asked. */
    suspend fun sync(id: RouteId): SyncOutcome {
        val key = SyncKey.Route(id)
        return locks.withLock(key) { syncLocked(id, key, lastUsedAt = clock.instant()) }
    }

    /**
     * App open: refreshes stale routes used within [StalenessPolicy.activeRouteWindow], one
     * after the other. Failures only go to the tracker. Does not count as a use, or a route
     * would stay active forever.
     */
    suspend fun refreshActiveRoutes() {
        val now = clock.instant()
        syncStateDao.getUsedSince(SyncKey.ROUTE_PREFIX, now - policy.activeRouteWindow)
            .filter { policy.isStale(it.syncedAt, policy.routeMaxAge, now) }
            .mapNotNull { RouteId.ofOrNull(it.key.removePrefix(SyncKey.ROUTE_PREFIX)) }
            .forEach { syncIfStale(it, markUsed = false) }
    }

    private suspend fun syncIfStale(id: RouteId, markUsed: Boolean): SyncOutcome {
        val key = SyncKey.Route(id)
        return locks.withLock(key) {
            val now = clock.instant()
            val state = syncStateDao.get(key.value)
            if (markUsed && state != null && isUseMarkDue(state.lastUsedAt, now)) syncStateDao.markUsed(key.value, now)
            val cached = dao.countPatterns(id.value) > 0
            val status = tracker.current(key)
            if (state != null && cached && !policy.isStale(state.syncedAt, policy.routeMaxAge, now)) {
                SyncOutcome.UpToDate
            } else if (policy.isBackingOff(status, now)) {
                SyncOutcome.Failed(checkNotNull(status.lastError))
            } else {
                syncLocked(id, key, lastUsedAt = if (markUsed) now else state?.lastUsedAt)
            }
        }
    }

    /**
     * Every write to `sync_state` re-runs each cached flow that reads it (all 2,753 stops too),
     * and the 14-day active window needs no finer use time than an hour.
     */
    private fun isUseMarkDue(lastUsedAt: Instant?, now: Instant): Boolean =
        lastUsedAt == null || lastUsedAt > now || Duration.between(lastUsedAt, now) >= USE_MARK_GRANULARITY

    private suspend fun syncLocked(id: RouteId, key: SyncKey, lastUsedAt: Instant?): SyncOutcome = tracker.track(key) {
        val fetched = fetch(id)
        val rows = fetched?.let { keepCachedWhereEmpty(id, it) }
        if (rows == null) {
            SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK)
        } else {
            dao.replaceRouteData(id.value, rows, SyncStateEntity(key.value, clock.instant(), lastUsedAt))
            SyncOutcome.Synced
        }
    }

    /** Null when the route came back with no patterns: nothing worth writing. */
    private suspend fun fetch(id: RouteId): RouteDataRows? = coroutineScope {
        val en = async { limited { gateway.route(id, Language.EN) } }
        val ka = async { limited { gateway.route(id, Language.KA) } }
        val detail = en.await()
        val suffixes = detail.patterns.map { it.suffix }.distinct()
        if (suffixes.isEmpty()) {
            ka.cancel()
            null
        } else {
            val stops = suffixes.map { async { limited { gateway.patternStops(id, it, Language.EN) } } }
            val schedules = suffixes.map { async { limited { gateway.schedule(id, it, Language.EN) } } }
            val polylines = async { limited { gateway.polylines(id, suffixes) } }
            routeDataRows(detail, ka.await(), stops.awaitAll(), schedules.awaitAll(), polylines.await())
        }
    }

    private suspend fun <T> limited(call: suspend () -> T): T = requests.withPermit { call() }

    /**
     * A pattern whose stops, timetable or shape came back empty keeps what the cache has for
     * it, so one bad response does not blank a direction; the other parts are still updated.
     */
    private suspend fun keepCachedWhereEmpty(id: RouteId, rows: RouteDataRows): RouteDataRows {
        var result = rows
        rows.patterns.map { it.suffix }.forEach { suffix ->
            if (result.patternStops.none { it.suffix == suffix }) {
                val cached = dao.getPatternStops(id.value, suffix)
                if (cached.isNotEmpty()) {
                    result =
                        result.copy(patternStops = result.patternStops + cached).also { logKept(id) }
                }
            }
            // Periods that all came back without a single stop row are as empty as no periods.
            if (result.stopTimes.none { it.suffix == suffix }) {
                val times = dao.getScheduleStopTimes(id.value, suffix)
                if (times.isNotEmpty()) {
                    val periods = dao.getSchedulePeriods(id.value, suffix)
                    result = result.copy(
                        periods = result.periods.filterNot { it.suffix == suffix } + periods,
                        stopTimes = result.stopTimes + times
                    )
                    logKept(id)
                }
            }
            if (result.polylines.none { it.suffix == suffix }) {
                val cached = dao.getPolylines(id.value).filter { it.suffix == suffix }
                if (cached.isNotEmpty()) {
                    result =
                        result.copy(polylines = result.polylines + cached).also { logKept(id) }
                }
            }
        }
        return result
    }

    private fun logKept(id: RouteId) {
        Log.w(TAG, "Kept cached rows for an empty pattern response of ${id.value}")
    }

    private companion object {
        const val MAX_REQUESTS_IN_FLIGHT = 4
        val USE_MARK_GRANULARITY: Duration = Duration.ofHours(1)
        const val TAG = "GzaRouteSync"
    }
}
