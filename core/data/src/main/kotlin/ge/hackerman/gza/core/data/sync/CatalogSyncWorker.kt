package ge.hackerman.gza.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ge.hackerman.gza.core.data.model.SyncOutcome
import java.time.Duration

/**
 * Keeps the stop and route catalogs current: weekly through [SyncScheduler], and once more
 * whenever app open finds them missing or overdue.
 */
@HiltWorker
internal class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalogSync: CatalogSync
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val periodic = inputData.getBoolean(KEY_PERIODIC, false)
        // The weekly run tolerates a day less than a week, so a one-time run a few days ago
        // does not push the refresh to almost two weeks; empty tables sync at any age.
        val outcome = if (periodic) catalogSync.syncIfStale(PERIODIC_MAX_AGE) else catalogSync.syncIfStale()
        return when (outcome) {
            SyncOutcome.Synced, SyncOutcome.UpToDate -> Result.success()

            is SyncOutcome.Failed -> when {
                !outcome.error.isRetryable() -> Result.failure()

                runAttemptCount < MAX_ATTEMPTS -> Result.retry()

                // Give up until the next period instead of retrying forever.
                else -> Result.success()
            }
        }
    }

    companion object {
        const val KEY_PERIODIC: String = "periodic"
        const val MAX_ATTEMPTS: Int = 5
        val PERIODIC_MAX_AGE: Duration = Duration.ofDays(6)

        fun periodicInput() = workDataOf(KEY_PERIODIC to true)
    }
}
