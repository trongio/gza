package ge.hackerman.gza.core.data.repository

import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.RouteBundle
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.StopId
import kotlinx.coroutines.flow.Flow

/** Routes and their timetables from the local cache. Only the refresh functions reach the network. */
interface RouteRepository {
    fun observeRoutes(): Flow<CachedResult<List<Route>>>

    fun observeRoute(id: RouteId): Flow<CachedResult<RouteBundle>>

    /** Null until the route has synced, or for a pattern it does not have. */
    fun observeSchedule(id: RouteId, pattern: PatternSuffix): Flow<RouteSchedule?>

    /**
     * Every cached timetable that stops at [stopId], one per (route, pattern). Each period holds
     * only the rows for [stopId] (a loop route can have two), and none when it skips the stop.
     */
    fun observeSchedulesAtStop(stopId: StopId): Flow<List<RouteSchedule>>

    /**
     * Call when a screen uses [id]: first use fetches it, then again when older than 12 h.
     * After a failure it waits 5 minutes (doubling to 2 h; 5 minutes flat when offline) before
     * trying again and returns the last error meanwhile; [refreshRoute] does not wait.
     */
    suspend fun refreshRouteIfStale(id: RouteId): SyncOutcome

    /** Pull to refresh: fetches now, whatever the age. */
    suspend fun refreshRoute(id: RouteId): SyncOutcome
}
