package ge.hackerman.gza.core.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** The only place that enqueues background sync. Unique work with KEEP: calling it again is free. */
@Singleton
internal class SyncScheduler @Inject constructor(private val workManager: WorkManager) {
    /**
     * Weekly, on any network with the battery not low. A new periodic request runs as soon as
     * its constraints hold, which covers the first launch; KEEP never resets the schedule.
     */
    fun ensurePeriodicCatalogSync() {
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodicRequest())
    }

    /**
     * Now, because the catalog is missing or overdue. Not expedited: below Android 12 that
     * needs a foreground notification, and a few seconds do not matter here.
     */
    fun requestCatalogSyncNow() {
        workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, oneTimeRequest())
    }

    companion object {
        const val PERIODIC_WORK: String = "catalog-sync-weekly"
        const val ONE_TIME_WORK: String = "catalog-sync-now"
        const val TAG: String = "catalog-sync"
        private val PERIOD: Duration = Duration.ofDays(7)
        private val BACKOFF: Duration = Duration.ofMinutes(15)

        internal fun periodicRequest(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<CatalogSyncWorker>(PERIOD)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .setInputData(CatalogSyncWorker.periodicInput())
            .addTag(TAG)
            .build()

        // The user is waiting for this one: no battery constraint.
        internal fun oneTimeRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .addTag(TAG)
            .build()
    }
}
