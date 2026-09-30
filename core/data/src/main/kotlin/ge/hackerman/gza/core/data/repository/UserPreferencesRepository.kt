package ge.hackerman.gza.core.data.repository

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.SavedStop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

/** The user's own data. Out-of-range values are clamped on write and again on read. */
interface UserPreferencesRepository {
    /** In display order. Entries with an invalid stop id are dropped, never a crash. */
    val savedStops: Flow<List<SavedStop>>
    val settings: Flow<UserSettings>

    /** No-op when [stopId] is already saved: its place and filter are kept. */
    suspend fun saveStop(stopId: StopId, walkMinutes: Int)
    suspend fun removeStop(stopId: StopId)

    /** Clamped to [SavedStop.WALK_MINUTES_RANGE]. No-op for a stop that is not saved. */
    suspend fun setWalkMinutes(stopId: StopId, minutes: Int)

    /** An empty set means every route. No-op for a stop that is not saved. */
    suspend fun setRouteFilter(stopId: StopId, routes: Set<RouteId>)

    /** [toIndex] is clamped to the list. No-op for a stop that is not saved. */
    suspend fun moveStop(stopId: StopId, toIndex: Int)

    /** Clamped to [UserSettings.BUFFER_MINUTES_RANGE]. */
    suspend fun setBufferMinutes(minutes: Int)
    suspend fun markFirstRunSuggestionHandled()
}
