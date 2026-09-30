package ge.hackerman.gza.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        scheduler = SyncScheduler { workManager }
    }

    private fun unique(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun `weekly sync is enqueued once and kept across calls`() {
        scheduler.ensurePeriodicCatalogSync()
        val first = unique(SyncScheduler.PERIODIC_WORK).single()
        scheduler.ensurePeriodicCatalogSync()
        val second = unique(SyncScheduler.PERIODIC_WORK).single()
        assertEquals(first.id, second.id)
        assertEquals(Duration.ofDays(7).toMillis(), second.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, second.constraints.requiredNetworkType)
        assertTrue(second.constraints.requiresBatteryNotLow())
        assertTrue(SyncScheduler.TAG in second.tags)
    }

    @Test
    fun `requests back off exponentially from 15 minutes`() {
        listOf(SyncScheduler.periodicRequest(), SyncScheduler.oneTimeRequest()).forEach {
            assertEquals(BackoffPolicy.EXPONENTIAL, it.workSpec.backoffPolicy)
            assertEquals(Duration.ofMinutes(15).toMillis(), it.workSpec.backoffDelayDuration)
        }
        assertTrue(SyncScheduler.periodicRequest().workSpec.input.getBoolean(CatalogSyncWorker.KEY_PERIODIC, false))
        assertFalse(SyncScheduler.oneTimeRequest().workSpec.expedited)
    }

    @Test
    fun `sync now is one unique job with only a network constraint`() {
        scheduler.requestCatalogSyncNow()
        scheduler.requestCatalogSyncNow()
        val info = unique(SyncScheduler.ONE_TIME_WORK).single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertFalse(info.constraints.requiresBatteryNotLow())
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun `building the scheduler does not resolve WorkManager`() {
        var resolved = false
        val lazyScheduler = SyncScheduler {
            resolved = true
            workManager
        }
        assertFalse(resolved)
        lazyScheduler.ensurePeriodicCatalogSync()
        assertTrue(resolved)
    }
}
