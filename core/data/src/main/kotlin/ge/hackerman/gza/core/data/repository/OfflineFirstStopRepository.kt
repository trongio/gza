package ge.hackerman.gza.core.data.repository

import ge.hackerman.gza.core.data.coroutines.DefaultDispatcher
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.toRoute
import ge.hackerman.gza.core.data.database.toStop
import ge.hackerman.gza.core.data.language.ContentLanguage
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.sync.StalenessPolicy
import ge.hackerman.gza.core.data.sync.StopRoutesSync
import ge.hackerman.gza.core.data.sync.SyncKey
import ge.hackerman.gza.core.data.sync.SyncStatusTracker
import ge.hackerman.gza.core.data.sync.cachedResult
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Room is the source of truth; the network only reaches it through the sync engines. */
@Suppress("LongParameterList") // Injected collaborators, one per concern.
internal class OfflineFirstStopRepository @Inject constructor(
    private val database: GzaDatabase,
    private val stopRoutesSync: StopRoutesSync,
    private val tracker: SyncStatusTracker,
    private val policy: StalenessPolicy,
    private val clock: Clock,
    private val contentLanguage: ContentLanguage,
    @DefaultDispatcher private val mappingDispatcher: CoroutineDispatcher
) : StopRepository {
    private val stopDao = database.stopDao()
    private val stopRoutesDao = database.stopRoutesDao()

    override fun observeStops(): Flow<CachedResult<List<Stop>>> = combine(
        database.invalidationTracker.createFlow("stops", "sync_state").map { stopDao.loadAll(SyncKey.Stops.value) },
        tracker.status(SyncKey.Stops),
        contentLanguage.language
    ) { snapshot, status, language ->
        val state = snapshot.syncState
        val stops = snapshot.rows.mapNotNull { it.toStop(language) }
        cachedResult(stops.ifEmpty { null }, state?.syncedAt, freshness(state?.syncedAt, policy.catalogMaxAge), status)
    }.flowOn(mappingDispatcher).distinctUntilChanged()

    override fun observeStop(id: StopId): Flow<Stop?> =
        combine(stopDao.observe(id.value), contentLanguage.language) { row, language -> row?.toStop(language) }
            .distinctUntilChanged()

    override fun observeStopRoutes(id: StopId): Flow<CachedResult<List<Route>>> {
        val key = SyncKey.StopRoutes(id)
        val snapshots = database.invalidationTracker
            .createFlow("stop_routes", "routes", "sync_state")
            .map { stopRoutesDao.loadSnapshot(id.value, key.value) }
        return combine(snapshots, tracker.status(key), contentLanguage.language) { snapshot, status, language ->
            val state = snapshot.syncState
            // A synced stop with no routes is data too (an empty list), not "nothing cached".
            val routes = snapshot.rows.mapNotNull { it.toRoute(language) }.takeIf { state != null || it.isNotEmpty() }
            cachedResult(routes, state?.syncedAt, freshness(state?.syncedAt, policy.stopRoutesMaxAge), status)
        }.flowOn(mappingDispatcher).distinctUntilChanged()
    }

    override suspend fun refreshStopRoutesIfStale(id: StopId): SyncOutcome = stopRoutesSync.syncIfStale(id)

    private fun freshness(syncedAt: Instant?, maxAge: Duration) = policy.freshness(syncedAt, maxAge, clock.instant())
}
