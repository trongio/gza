package ge.hackerman.gza.core.data.model

/** The result of one refresh attempt. */
sealed interface SyncOutcome {
    /** New data was written. */
    data object Synced : SyncOutcome

    /** Skipped: the cache is not stale yet. */
    data object UpToDate : SyncOutcome

    /** Nothing was written; whatever was cached before is still there. */
    data class Failed(val error: SyncError) : SyncOutcome
}
