package ge.hackerman.gza.core.data.repository

import androidx.datastore.core.DataStore
import ge.hackerman.gza.core.data.datastore.SavedStopData
import ge.hackerman.gza.core.data.datastore.UserPreferencesData
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.SavedStop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.UserSettings
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<UserPreferencesData>
) : UserPreferencesRepository {
    // DataStore's documented pattern: a read error shows the defaults instead of failing
    // every collector. Anything else is a bug and propagates.
    private val data: Flow<UserPreferencesData> = dataStore.data.catch { e ->
        if (e is IOException) emit(UserPreferencesData()) else throw e
    }

    override val savedStops: Flow<List<SavedStop>> = data
        .map { prefs -> prefs.sanitized().savedStops.map { it.toDomain() } }
        .distinctUntilChanged()

    override val settings: Flow<UserSettings> = data
        .map { UserSettings(clampBuffer(it.bufferMinutes), it.firstRunSuggestionHandled) }
        .distinctUntilChanged()

    override suspend fun saveStop(stopId: StopId, walkMinutes: Int) = update { prefs ->
        if (prefs.savedStops.any { it.stopId == stopId.value }) {
            prefs
        } else {
            prefs.copy(savedStops = prefs.savedStops + SavedStopData(stopId.value, clampWalk(walkMinutes)))
        }
    }

    override suspend fun removeStop(stopId: StopId) = update { prefs ->
        prefs.copy(savedStops = prefs.savedStops.filterNot { it.stopId == stopId.value })
    }

    override suspend fun setWalkMinutes(stopId: StopId, minutes: Int) = updateStop(stopId) {
        it.copy(walkMinutes = clampWalk(minutes))
    }

    override suspend fun setRouteFilter(stopId: StopId, routes: Set<RouteId>) = updateStop(stopId) {
        it.copy(routeFilter = routes.map(RouteId::value).sorted())
    }

    override suspend fun moveStop(stopId: StopId, toIndex: Int) = update { prefs ->
        val stops = prefs.savedStops.toMutableList()
        val from = stops.indexOfFirst { it.stopId == stopId.value }
        if (from < 0) {
            prefs
        } else {
            val moved = stops.removeAt(from)
            stops.add(toIndex.coerceIn(0, stops.size), moved)
            prefs.copy(savedStops = stops)
        }
    }

    override suspend fun setBufferMinutes(minutes: Int) = update { it.copy(bufferMinutes = clampBuffer(minutes)) }

    override suspend fun markFirstRunSuggestionHandled() = update { it.copy(firstRunSuggestionHandled = true) }

    private suspend fun updateStop(stopId: StopId, change: (SavedStopData) -> SavedStopData) = update { prefs ->
        prefs.copy(savedStops = prefs.savedStops.map { if (it.stopId == stopId.value) change(it) else it })
    }

    // Every write starts from the cleaned-up file, so indices always match what savedStops shows.
    private suspend fun update(change: (UserPreferencesData) -> UserPreferencesData) {
        dataStore.updateData { change(it.sanitized()) }
    }
}

/** Drops invalid and repeated stop ids and clamps every number, keeping order. */
private fun UserPreferencesData.sanitized(): UserPreferencesData = copy(
    savedStops = savedStops
        .filter { StopId.ofOrNull(it.stopId) != null }
        .distinctBy { it.stopId }
        .map { stop ->
            stop.copy(
                walkMinutes = clampWalk(stop.walkMinutes),
                routeFilter = stop.routeFilter.filter { RouteId.ofOrNull(it) != null }.distinct()
            )
        },
    bufferMinutes = clampBuffer(bufferMinutes)
)

private fun SavedStopData.toDomain(): SavedStop =
    SavedStop(StopId(stopId), clampWalk(walkMinutes), routeFilter.mapNotNull(RouteId::ofOrNull).toSet())

private fun clampWalk(minutes: Int): Int = minutes.coerceIn(SavedStop.WALK_MINUTES_RANGE)

private fun clampBuffer(minutes: Int): Int = minutes.coerceIn(UserSettings.BUFFER_MINUTES_RANGE)
