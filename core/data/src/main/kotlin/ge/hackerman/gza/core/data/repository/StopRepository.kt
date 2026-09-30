package ge.hackerman.gza.core.data.repository

import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import kotlinx.coroutines.flow.Flow

/** Stops from the local cache, names in the current [ge.hackerman.gza.core.data.language.ContentLanguage]. */
interface StopRepository {
    /** The whole catalog, synced weekly in the background. */
    fun observeStops(): Flow<CachedResult<List<Stop>>>

    /** Null when unknown, for example a saved stop the network no longer serves. */
    fun observeStop(id: StopId): Flow<Stop?>

    fun observeStopRoutes(id: StopId): Flow<CachedResult<List<Route>>>

    /**
     * Call when a screen shows [id]'s routes; a no-op while the cache is fresh, and while a
     * recent failure backs off (5 minutes doubling to 2 h, 5 minutes flat when offline; the last
     * error is returned). [refreshStopRoutes] does not wait.
     */
    suspend fun refreshStopRoutesIfStale(id: StopId): SyncOutcome

    /** Pull to refresh and retry buttons: fetches now, whatever the age or the backoff. */
    suspend fun refreshStopRoutes(id: StopId): SyncOutcome
}
