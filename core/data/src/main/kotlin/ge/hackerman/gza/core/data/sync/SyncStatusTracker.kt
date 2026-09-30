package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal data class KeyStatus(
    val inFlight: Boolean,
    val lastError: SyncError?,
    val lastAttemptAt: Instant?,
    /** Failed attempts since the last success; sizes the retry backoff (see [StalenessPolicy.errorBackoff]). */
    val consecutiveFailures: Int = 0
) {
    companion object {
        val NONE = KeyStatus(inFlight = false, lastError = null, lastAttemptAt = null)
    }
}

/**
 * Which syncs are running and how the last attempt of each ended. In memory on purpose: after
 * a restart the first attempt is fresh anyway, and a persisted error would show yesterday's
 * "offline" today.
 */
@Singleton
internal class SyncStatusTracker @Inject constructor(private val clock: Clock) {
    private val state = MutableStateFlow<Map<SyncKey, KeyStatus>>(emptyMap())

    val statuses: StateFlow<Map<SyncKey, KeyStatus>> = state.asStateFlow()

    fun status(key: SyncKey): Flow<KeyStatus> = state.map { it[key] ?: KeyStatus.NONE }.distinctUntilChanged()

    fun current(key: SyncKey): KeyStatus = state.value[key] ?: KeyStatus.NONE

    fun begin(key: SyncKey) {
        val now = clock.instant()
        state.update { it + (key to (it[key] ?: KeyStatus.NONE).copy(inFlight = true, lastAttemptAt = now)) }
    }

    fun end(key: SyncKey, outcome: SyncOutcome) {
        val error = (outcome as? SyncOutcome.Failed)?.error
        state.update {
            val old = it[key] ?: KeyStatus.NONE
            val failures = if (error == null) 0 else old.consecutiveFailures + 1
            it + (key to old.copy(inFlight = false, lastError = error, consecutiveFailures = failures))
        }
    }

    /** A cancelled attempt: not running any more, and it says nothing new about the data. */
    fun abandon(key: SyncKey) {
        state.update { it + (key to (it[key] ?: KeyStatus.NONE).copy(inFlight = false)) }
    }

    /**
     * Runs one attempt for [key] with its status kept up to date. Failures the data layer knows
     * become [SyncOutcome.Failed]; cancellation and bugs propagate (see [toSyncError]).
     */
    @Suppress("TooGenericExceptionCaught") // toSyncError rethrows anything it does not know.
    suspend fun track(key: SyncKey, attempt: suspend () -> SyncOutcome): SyncOutcome {
        begin(key)
        var outcome: SyncOutcome? = null
        try {
            outcome = try {
                attempt()
            } catch (e: Exception) {
                SyncOutcome.Failed(e.toSyncError())
            }
            return outcome
        } finally {
            if (outcome != null) end(key, outcome) else abandon(key)
        }
    }
}

/**
 * The table in docs/tasks/plans/T04.md 3.6: data wins whenever there is any; without data, a
 * failed last attempt is [CachedResult.Unavailable] and anything else is [CachedResult.Loading].
 * [value] is null when Room has nothing to show.
 */
internal fun <T : Any> cachedResult(
    value: T?,
    syncedAt: Instant?,
    freshness: Freshness,
    status: KeyStatus
): CachedResult<T> {
    val error = status.lastError
    return when {
        value != null -> CachedResult.Data(value, syncedAt, freshness, error)
        !status.inFlight && error != null -> CachedResult.Unavailable(error)
        else -> CachedResult.Loading
    }
}
