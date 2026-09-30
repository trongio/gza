package ge.hackerman.gza.core.data.model

import java.time.Instant

/**
 * What a screen shows for cached data: the data with how fresh it is, or why there is none.
 * Built from Room plus the in-memory sync status.
 */
sealed interface CachedResult<out T> {
    /** Nothing cached yet, and a sync is running or about to. */
    data object Loading : CachedResult<Nothing>

    /**
     * Cached data, always worth showing. [refreshError] is set when the last refresh failed,
     * for a quiet "offline, showing saved data" hint.
     */
    data class Data<out T>(
        val value: T,
        val syncedAt: Instant?,
        val freshness: Freshness,
        val refreshError: SyncError?
    ) : CachedResult<T>

    /** Nothing cached, and the last attempt to get it failed. */
    data class Unavailable(val error: SyncError) : CachedResult<Nothing>
}

enum class Freshness { FRESH, STALE }
