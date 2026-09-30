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
import ge.hackerman.gza.core.data.testing.failInsertOf
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Retry for anything the network may fix, fail for a storage error SQLite really throws. */
@RunWith(AndroidJUnit4::class)
class CatalogSyncWorkerErrorsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph = DataTestGraph(TestDatabase.inMemory())

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

    private fun failingWith(failure: TtcGatewayException): Result {
        graph.gateway.failure = failure
        return run()
    }

    @Test
    fun `server errors, a dead key and a malformed body are retried`() {
        assertEquals(Result.retry(), failingWith(TtcGatewayException.Http(500, null)))
        assertEquals(Result.retry(), failingWith(TtcGatewayException.Http(503, null)))
        assertEquals(Result.retry(), failingWith(TtcGatewayException.Http(401, null)))
        assertEquals(Result.retry(), failingWith(TtcGatewayException.NoKey(IOException())))
        assertEquals(Result.retry(), failingWith(TtcGatewayException.Malformed(null)))
    }

    @Test
    fun `an empty response is retried`() {
        graph.gateway.stopsOverride = { emptyList() }
        assertEquals(Result.retry(), run())
    }

    @Test
    fun `the periodic run retries the network too, and gives up quietly on the fifth attempt`() {
        graph.gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(Result.retry(), run(periodic = true, attempt = 4))
        assertEquals(Result.success(), run(periodic = true, attempt = 5))
        assertEquals(Result.success(), run(periodic = true, attempt = 50))
    }

    @Test
    fun `a real SQLite failure while writing is a failure, not a retry, on any attempt`() = runBlocking {
        graph.db.failInsertOf("stops", "1:970")
        assertEquals(Result.failure(), run())
        assertEquals(Result.failure(), run(attempt = 7))
        assertEquals(Result.failure(), run(periodic = true))
        assertEquals(0, graph.db.stopDao().count())
    }

    @Test
    fun `the periodic threshold is exactly 6 days`() = runBlocking {
        assertEquals(Result.success(), run())
        graph.gateway.calls.clear()
        graph.clock.advanceBy(Duration.ofDays(6).minusMillis(1))
        assertEquals(Result.success(), run(periodic = true))
        assertEquals(0, graph.gateway.calls.size)
        graph.clock.advanceBy(Duration.ofMillis(1))
        assertEquals(Result.success(), run(periodic = true))
        assertEquals(4, graph.gateway.calls.size)
    }
}
