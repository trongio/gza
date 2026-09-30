package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.entity.StopRouteEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.data.database.toEntity
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Which routes serve a stop, so a saved stop's routes are known offline. */
@Singleton
internal class StopRoutesSync @Inject constructor(
    private val gateway: TtcGatewayClient,
    database: GzaDatabase,
    private val tracker: SyncStatusTracker,
    private val policy: StalenessPolicy,
    private val clock: Clock
) {
    private val dao = database.stopRoutesDao()
    private val syncStateDao = database.syncStateDao()
    private val locks = KeyedMutex<SyncKey>()

    suspend fun syncIfStale(stopId: StopId): SyncOutcome {
        val key = SyncKey.StopRoutes(stopId)
        return locks.withLock(key) {
            val syncedAt = syncStateDao.get(key.value)?.syncedAt
            val now = clock.instant()
            val status = tracker.current(key)
            if (syncedAt != null && !policy.isStale(syncedAt, policy.stopRoutesMaxAge, now)) {
                SyncOutcome.UpToDate
            } else if (policy.isBackingOff(status, now)) {
                SyncOutcome.Failed(checkNotNull(status.lastError))
            } else {
                tracker.track(key) {
                    val routes = gateway.stopRoutes(stopId, Language.EN).distinctBy { it.id }
                    if (routes.isEmpty() && dao.getRouteIds(stopId.value).isNotEmpty()) {
                        SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK)
                    } else {
                        dao.replaceForStop(
                            stopId = stopId.value,
                            rows = routes.map { StopRouteEntity(stopId.value, it.id.value) },
                            // English only: the catalog adds Georgian names and wins when it runs.
                            routesIfAbsent = routes.map { it.toEntity(longNameEn = it.longName, longNameKa = null) },
                            syncState = SyncStateEntity(key.value, clock.instant())
                        )
                        SyncOutcome.Synced
                    }
                }
            }
        }
    }
}
