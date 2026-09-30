package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.data.database.mergeRoutes
import ge.hackerman.gza.core.data.database.mergeStops
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Refreshes the stop and route catalogs, each on its own: routes are written even when stops
 * fail. Nothing replaces a table unless both languages arrived and the new list is plausible
 * (see [MIN_RETAINED_FRACTION]), so a bad response never empties the cache.
 */
@Singleton
internal class CatalogSync @Inject constructor(
    private val gateway: TtcGatewayClient,
    database: GzaDatabase,
    private val tracker: SyncStatusTracker,
    private val policy: StalenessPolicy,
    private val clock: Clock
) {
    private val stopDao = database.stopDao()
    private val routeDao = database.routeDao()
    private val syncStateDao = database.syncStateDao()
    private val locks = KeyedMutex<SyncKey>()

    /** Refreshes whichever of stops and routes is older than [maxAge] or empty. */
    suspend fun syncIfStale(maxAge: Duration = policy.catalogMaxAge): SyncOutcome = coroutineScope {
        val stops = async {
            syncTable(SyncKey.Stops, maxAge, stopDao::count, stopDao::replaceAll) {
                val en = async { gateway.stops(Language.EN) }
                val ka = async { gateway.stops(Language.KA) }
                Fetched(en.await().size, ka.await().size, mergeStops(en.await(), ka.await()))
            }
        }
        val routes = async {
            syncTable(SyncKey.Routes, maxAge, routeDao::count, routeDao::replaceAll) {
                val en = async { gateway.routes(Language.EN) }
                val ka = async { gateway.routes(Language.KA) }
                Fetched(en.await().size, ka.await().size, mergeRoutes(en.await(), ka.await()))
            }
        }
        worse(stops.await(), routes.await())
    }

    private class Fetched<R>(val enCount: Int, val kaCount: Int, val rows: List<R>)

    private suspend fun <R> syncTable(
        key: SyncKey,
        maxAge: Duration,
        cachedCount: suspend () -> Int,
        write: suspend (List<R>, SyncStateEntity) -> Unit,
        fetch: suspend CoroutineScope.() -> Fetched<R>
    ): SyncOutcome = locks.withLock(key) {
        val cached = cachedCount()
        val syncedAt = syncStateDao.get(key.value)?.syncedAt
        // Re-checked inside the lock: a second caller finds the first one's result.
        if (cached > 0 && !policy.isStale(syncedAt, maxAge, clock.instant())) {
            SyncOutcome.UpToDate
        } else {
            tracker.track(key) {
                val fetched = coroutineScope { fetch() }
                if (isPlausible(fetched, cached)) {
                    write(fetched.rows, SyncStateEntity(key.value, clock.instant()))
                    SyncOutcome.Synced
                } else {
                    SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK)
                }
            }
        }
    }

    // A Georgian list much shorter than the English one would wipe the Georgian names.
    private fun isPlausible(fetched: Fetched<*>, cached: Int): Boolean = fetched.rows.isNotEmpty() &&
        fetched.rows.size >= cached * MIN_RETAINED_FRACTION &&
        fetched.kaCount >= fetched.enCount * MIN_RETAINED_FRACTION

    private fun worse(a: SyncOutcome, b: SyncOutcome): SyncOutcome = when {
        a is SyncOutcome.Failed -> a
        b is SyncOutcome.Failed -> b
        a is SyncOutcome.Synced || b is SyncOutcome.Synced -> SyncOutcome.Synced
        else -> SyncOutcome.UpToDate
    }

    companion object {
        /**
         * A new list must keep at least this share of the cached rows. The network changes by a
         * handful of stops a week; a truncated or filtered response is far likelier than half
         * the city disappearing. A first sync (empty table) accepts any non-empty list.
         */
        const val MIN_RETAINED_FRACTION: Double = 0.5
    }
}
