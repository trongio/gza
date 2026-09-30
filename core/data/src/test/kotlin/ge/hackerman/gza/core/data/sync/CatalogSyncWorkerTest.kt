package ge.hackerman.gza.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogSyncWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = DataTestGraph(TestDatabase.inMemory())

    // What HiltWorkerFactory does in the app, with the test graph's CatalogSync.
    private val factory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            params: WorkerParameters
        ): ListenableWorker = CatalogSyncWorker(appContext, params, graph.catalogSync)
    }

    @After
    fun tearDown() = graph.db.close()

    private fun run(periodic: Boolean = false, attempt: Int = 0): Result = runBlocking {
        TestListenableWorkerBuilder<CatalogSyncWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(attempt)
            .apply { if (periodic) setInputData(CatalogSyncWorker.periodicInput()) }
            .build()
            .doWork()
    }

    @Test
    fun `a sync succeeds and fills the catalog`() = runBlocking {
        assertEquals(Result.success(), run())
        assertEquals(2753, graph.db.stopDao().count())
        assertEquals(280, graph.db.routeDao().count())
    }

    @Test
    fun `offline is retried with backoff, then given up until the next period`() {
        graph.gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(Result.retry(), run(attempt = 0))
        assertEquals(Result.retry(), run(attempt = 4))
        assertEquals(Result.success(), run(attempt = 5))
    }

    @Test
    fun `a storage failure is not retried`() {
        graph.gateway.stopsOverride = { throw IOException("disk full") }
        assertEquals(Result.failure(), run())
    }

    @Test
    fun `the periodic run refreshes after 6 days, a one-time run waits for 7`() {
        run()
        graph.gateway.calls.clear()
        graph.clock.advanceBy(Duration.ofDays(6))
        assertEquals(Result.success(), run(periodic = false))
        assertTrue(graph.gateway.calls.isEmpty())
        assertEquals(Result.success(), run(periodic = true))
        assertEquals(4, graph.gateway.calls.size)
    }
}
