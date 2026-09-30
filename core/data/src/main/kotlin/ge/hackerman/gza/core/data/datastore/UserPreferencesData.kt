package ge.hackerman.gza.core.data.datastore

import ge.hackerman.gza.core.model.UserSettings
import kotlinx.serialization.Serializable

/** What `user_prefs.json` holds. Plain strings and ints so any old or hand-edited file still reads. */
@Serializable
internal data class UserPreferencesData(
    /** List order is display order. */
    val savedStops: List<SavedStopData> = emptyList(),
    val bufferMinutes: Int = UserSettings.DEFAULT_BUFFER_MINUTES,
    val firstRunSuggestionHandled: Boolean = false
)

@Serializable
internal data class SavedStopData(
    val stopId: String,
    val walkMinutes: Int,
    /** Empty means every route at the stop. */
    val routeFilter: List<String> = emptyList()
)
