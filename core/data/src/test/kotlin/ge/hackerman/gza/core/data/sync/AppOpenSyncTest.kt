package ge.hackerman.gza.core.data.sync

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOpenSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private lateinit var workManager: WorkManager
    private lateinit var appOpen: AppOpenSync

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        appOpen = AppOpenSync(
            SyncScheduler { workManager },
            graph.routeSync,
            graph.db,
            graph.language,
            graph.policy,
            graph.clock,
            scope
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        graph.db.close()
    }

    private fun enqueued(name: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(name).get().filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `an empty catalog asks for a sync now and schedules the weekly one`() = runBlocking {
        appOpen.run()
        assertEquals(1, enqueued(SyncScheduler.PERIODIC_WORK).size)
        assertEquals(1, enqueued(SyncScheduler.ONE_TIME_WORK).size)
    }

    @Test
    fun `a catalog synced 3 days ago needs nothing now`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.clock.advanceBy(Duration.ofDays(3))
        appOpen.run()
        assertEquals(1, enqueued(SyncScheduler.PERIODIC_WORK).size)
        assertTrue(enqueued(SyncScheduler.ONE_TIME_WORK).isEmpty())
    }

    @Test
    fun `a catalog 9 days old means the weekly job is late, so sync now`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.clock.advanceBy(Duration.ofDays(9))
        appOpen.run()
        assertEquals(1, enqueued(SyncScheduler.ONE_TIME_WORK).size)
    }

    @Test
    fun `active stale routes are refreshed and the language is re-read`() = runBlocking {
        val r326 = FixtureDomain.routeId("326")
        graph.routeSync.syncIfStale(r326)
        graph.clock.advanceBy(Duration.ofHours(13))
        graph.gateway.calls.clear()
        appOpen.run()
        assertEquals(7, graph.gateway.calls.size)
        assertEquals(graph.clock.now, graph.db.syncStateDao().get("route:${r326.value}")?.syncedAt)
        assertEquals(1, graph.language.refreshes)
    }

    @Test
    fun `a failing step never escapes and the other steps still run`() = runBlocking {
        val r326 = FixtureDomain.routeId("326")
        graph.routeSync.syncIfStale(r326)
        graph.clock.advanceBy(Duration.ofHours(13))
        // Not a gateway failure: a bug the sync rethrows.
        graph.gateway.routeOverride = { _, _ -> error("bug") }
        appOpen.run()
        assertEquals(1, graph.language.refreshes)
        assertEquals(1, enqueued(SyncScheduler.PERIODIC_WORK).size)
    }

    @Test
    fun `coming to the foreground runs it in the application scope`() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        appOpen.onStart(owner)
        assertEquals(1, graph.language.refreshes)
        assertEquals(1, enqueued(SyncScheduler.PERIODIC_WORK).size)
    }
}
