package ge.hackerman.gza.core.data.repository

import ge.hackerman.gza.core.data.coroutines.DefaultDispatcher
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.dao.RouteDataSnapshot
import ge.hackerman.gza.core.data.database.toPattern
import ge.hackerman.gza.core.data.database.toPolyline
import ge.hackerman.gza.core.data.database.toRoute
import ge.hackerman.gza.core.data.database.toSchedules
import ge.hackerman.gza.core.data.database.toStop
import ge.hackerman.gza.core.data.language.ContentLanguage
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.RouteBundle
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.sync.RouteSync
import ge.hackerman.gza.core.data.sync.StalenessPolicy
import ge.hackerman.gza.core.data.sync.SyncKey
import ge.hackerman.gza.core.data.sync.SyncStatusTracker
import ge.hackerman.gza.core.data.sync.cachedResult
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.StopId
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Suppress("LongParameterList") // Injected collaborators, one per concern.
internal class OfflineFirstRouteRepository @Inject constructor(
    private val database: GzaDatabase,
    private val routeSync: RouteSync,
    private val tracker: SyncStatusTracker,
    private val policy: StalenessPolicy,
    private val clock: Clock,
    private val contentLanguage: ContentLanguage,
    @DefaultDispatcher private val mappingDispatcher: CoroutineDispatcher
) : RouteRepository {
    private val routeDao = database.routeDao()
    private val routeDataDao = database.routeDataDao()
    private val scheduleDao = database.scheduleDao()
    private val syncStateDao = database.syncStateDao()

    override fun observeRoutes(): Flow<CachedResult<List<Route>>> = combine(
        routeDao.observeAll(),
        syncStateDao.observe(SyncKey.Routes.value),
        tracker.status(SyncKey.Routes),
        contentLanguage.language
    ) { rows, state, status, language ->
        val routes = rows.mapNotNull { it.toRoute(language) }
        val freshness = policy.freshness(state?.syncedAt, policy.catalogMaxAge, clock.instant())
        cachedResult(routes.ifEmpty { null }, state?.syncedAt, freshness, status)
    }.flowOn(mappingDispatcher).distinctUntilChanged()

    override fun observeRoute(id: RouteId): Flow<CachedResult<RouteBundle>> {
        val key = SyncKey.Route(id)
        // One read transaction per change of any of these tables, so a bundle never mixes two syncs.
        val snapshots = database.invalidationTracker
            .createFlow("routes", "patterns", "pattern_stops", "polylines", "stops")
            .map { routeDataDao.loadRouteData(id.value) }
        return combine(
            snapshots,
            syncStateDao.observe(key.value),
            tracker.status(key),
            contentLanguage.language
        ) { snapshot, state, status, language ->
            val freshness = policy.freshness(state?.syncedAt, policy.routeMaxAge, clock.instant())
            cachedResult(snapshot.toBundle(id, language), state?.syncedAt, freshness, status)
        }.flowOn(mappingDispatcher).distinctUntilChanged()
    }

    override fun observeSchedule(id: RouteId, pattern: PatternSuffix): Flow<RouteSchedule?> =
        scheduleDao.observeSchedule(id.value, pattern.value)
            .map { it.toSchedules().singleOrNull() }
            .flowOn(mappingDispatcher)
            .distinctUntilChanged()

    override fun observeSchedulesAtStop(stopId: StopId): Flow<List<RouteSchedule>> =
        scheduleDao.observeAtStop(stopId.value)
            .map { it.toSchedules() }
            .flowOn(mappingDispatcher)
            .distinctUntilChanged()

    override suspend fun refreshRouteIfStale(id: RouteId): SyncOutcome = routeSync.syncIfStale(id)

    override suspend fun refreshRoute(id: RouteId): SyncOutcome = routeSync.sync(id)

    private fun RouteDataSnapshot.toBundle(id: RouteId, language: Language): RouteBundle? {
        val route = route?.toRoute(language)
        val patterns = patterns.mapNotNull { it.toPattern(language) }
        if (route == null || patterns.isEmpty()) return null
        val stopsByPattern = patternStops.groupBy { it.suffix }.mapNotNull { (suffix, rows) ->
            PatternSuffix.ofOrNull(suffix)?.let {
                PatternStops(id, it, rows.mapNotNull { row -> row.toStop(language) })
            }
        }
        return RouteBundle(
            route = route,
            detail = RouteDetail(id, route.shortName, route.color, route.kind, patterns, defaultPattern = null),
            patternStops = stopsByPattern.associateBy { it.pattern },
            polylines = polylines.mapNotNull { it.toPolyline() }.associateBy { it.pattern }
        )
    }
}
