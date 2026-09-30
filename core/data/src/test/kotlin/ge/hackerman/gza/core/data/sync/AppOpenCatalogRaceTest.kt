package ge.hackerman.gza.core.data.sync

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App open's in-process catalog sync against the WorkManager worker and against itself: one
 * download per table whoever asks, and a device that keeps coming to the foreground offline
 * does not turn every open into requests.
 */
@RunWith(AndroidJUnit4::class)
class AppOpenCatalogRaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val r326 = FixtureDomain.routeId("326")
    private lateinit var workManager: WorkManager
    private lateinit var appOpen: AppOpenSync

    // What HiltWorkerFactory does in the app, with the test graph's CatalogSync.
    private val factory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            params: WorkerParameters
        ): ListenableWorker = CatalogSyncWorker(appContext, params, graph.catalogSync)
    }

    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        appOpen = graph.appOpenSync(workManager, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        graph.db.close()
    }

    private suspend fun runWorker(periodic: Boolean): Result = TestListenableWorkerBuilder<CatalogSyncWorker>(context)
        .setWorkerFactory(factory)
        .apply { if (periodic) setInputData(CatalogSyncWorker.periodicInput()) }
        .build()
        .doWork()

    private fun assertOneRequestPerTableAndLanguage() {
        listOf("stops EN", "stops KA", "routes EN", "routes KA").forEach {
            assertEquals("requests for $it in ${gateway.calls}", 1, gateway.callsTo(it).size)
        }
        assertEquals(4, gateway.calls.size)
    }

    private suspend fun awaitLaunchedOpens() = withTimeout(TIMEOUT_MS) {
        scope.coroutineContext.job.children.toList().forEach { it.join() }
    }

    private fun enqueuedOneTime(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(SyncScheduler.ONE_TIME_WORK)
        .get()
        .filter { it.state == WorkInfo.State.ENQUEUED }

    @Test
    fun `app open first, then the worker while it downloads, fetch each table once`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val open = async { appOpen.run() }
        while (gateway.calls.size < 4) yield()
        val worker = async { runWorker(periodic = false) }
        delay(SETTLE_MS) // lets the worker reach the table locks
        gate.complete(Unit)
        open.await()
        assertEquals(Result.success(), worker.await())
        assertOneRequestPerTableAndLanguage()
        assertEquals(2753, graph.db.stopDao().count())
        assertEquals(280, graph.db.routeDao().count())
    }

    @Test
    fun `the weekly worker first, then app open while it downloads, fetch each table once`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val worker = async { runWorker(periodic = true) }
        while (gateway.calls.size < 4) yield()
        val open = async { appOpen.run() }
        delay(SETTLE_MS)
        gate.complete(Unit)
        open.await()
        assertEquals(Result.success(), worker.await())
        assertOneRequestPerTableAndLanguage()
        assertTrue(enqueuedOneTime().isEmpty())
    }

    @Test
    fun `app open and the worker on real threads, many times over, still fetch each table once`() = runBlocking {
        val outcomes = List(REPEATS) { index ->
            if (index % 2 == 0) scope.async { appOpen.run() } else scope.async { runWorker(periodic = index % 3 == 0) }
        }
        outcomes.forEach { it.await() }
        assertOneRequestPerTableAndLanguage()
    }

    @Test
    fun `five foregrounds at once while offline make one request per table`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        gateway.failure = TtcGatewayException.Network(IOException())
        repeat(5) { appOpen.onStart(owner) }
        while (gateway.callsTo("stops EN").isEmpty() || gateway.callsTo("routes EN").isEmpty()) yield()
        delay(SETTLE_MS) // the other four queue on the table locks
        gate.complete(Unit)
        awaitLaunchedOpens()
        assertEquals(gateway.calls.toString(), 1, gateway.callsTo("stops EN").size)
        assertEquals(gateway.calls.toString(), 1, gateway.callsTo("routes EN").size)
        assertEquals(1, graph.tracker.current(SyncKey.Stops).consecutiveFailures)
        assertEquals(1, enqueuedOneTime().size)
        assertEquals(CachedResult.Unavailable(SyncError.OFFLINE), graph.stops.observeStops().first())
    }

    @Test
    fun `opening the app every minute for 3 hours offline tries only at 0, 5, 15, 35, 75 and 155 minutes`() =
        runBlocking {
            // Used online a week ago: the catalog is overdue and the route stale on every open.
            graph.catalogSync.syncIfStale()
            graph.routes.refreshRouteIfStale(r326)
            graph.clock.advanceBy(Duration.ofDays(8))
            gateway.failure = TtcGatewayException.Network(IOException())
            gateway.calls.clear()

            val start = graph.clock.now
            val stopAttempts = mutableListOf<Long>()
            val routeAttempts = mutableListOf<Long>()
            repeat(MINUTES_OFFLINE) {
                val stopsBefore = gateway.callsTo("stops EN").size
                val routeBefore = gateway.callsTo("route ${r326.value} EN").size
                appOpen.run()
                val minute = Duration.between(start, graph.clock.now).toMinutes()
                if (gateway.callsTo("stops EN").size > stopsBefore) stopAttempts += minute
                if (gateway.callsTo("route ${r326.value} EN").size > routeBefore) routeAttempts += minute
                graph.clock.advanceBy(Duration.ofMinutes(1))
            }
            val expected = listOf(0L, 5L, 15L, 35L, 75L, 155L)
            assertEquals(expected, stopAttempts)
            assertEquals(expected, routeAttempts)
            assertEquals(1, enqueuedOneTime().size)

            // Online again: the next open past the window syncs everything, the one after is free.
            gateway.failure = null
            graph.clock.now = start + Duration.ofMinutes(155 + 120)
            gateway.calls.clear()
            appOpen.run()
            assertEquals(graph.clock.now, graph.db.syncStateDao().get(SyncKey.Stops.value)?.syncedAt)
            assertEquals(graph.clock.now, graph.db.syncStateDao().get("route:${r326.value}")?.syncedAt)
            gateway.calls.clear()
            graph.clock.advanceBy(Duration.ofMinutes(1))
            appOpen.run()
            assertTrue(gateway.calls.toString(), gateway.calls.isEmpty())
        }

    private companion object {
        const val SETTLE_MS = 200L
        const val TIMEOUT_MS = 10_000L
        const val REPEATS = 12
        const val MINUTES_OFFLINE = 180
    }
}
