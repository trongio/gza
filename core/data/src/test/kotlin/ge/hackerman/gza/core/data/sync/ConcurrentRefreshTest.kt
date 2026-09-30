package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.StopId
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Many callers asking for the same refresh at once cost one set of requests and stay consistent. */
@RunWith(AndroidJUnit4::class)
class ConcurrentRefreshTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val r326 = FixtureDomain.routeId("326")
    private val s970 = StopId(FixtureDomain.STOP_970)

    @After
    fun tearDown() = graph.db.close()

    @Test
    fun `five screens opening the same route at once make one route sync`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val callers = List(5) { async { graph.routes.refreshRouteIfStale(r326) } }
        while (gateway.calls.isEmpty()) yield()
        gate.complete(Unit)
        val outcomes = callers.awaitAll()
        assertEquals(1, outcomes.count { it == SyncOutcome.Synced })
        assertEquals(4, outcomes.count { it == SyncOutcome.UpToDate })
        assertEquals(7, gateway.calls.size)
    }

    @Test
    fun `the same on real threads`() = runBlocking {
        val outcomes = withContext(Dispatchers.IO) {
            List(8) { async { graph.routeSync.syncIfStale(r326) } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it == SyncOutcome.Synced })
        assertEquals(7, gateway.calls.size)
        assertEquals(2, graph.db.routeDataDao().countPatterns(r326.value))
    }

    @Test
    fun `app open refreshing a route while a screen asks for it syncs it once`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        graph.clock.advanceBy(Duration.ofHours(13))
        gateway.calls.clear()
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val appOpen = async { graph.routeSync.refreshActiveRoutes() }
        val screen = async { graph.routes.refreshRouteIfStale(r326) }
        while (gateway.calls.isEmpty()) yield()
        gate.complete(Unit)
        appOpen.await()
        // Whichever takes the lock first syncs; the other finds it fresh.
        assertTrue(screen.await() in setOf(SyncOutcome.Synced, SyncOutcome.UpToDate))
        assertEquals(7, gateway.calls.size)
    }

    @Test
    fun `a stop's routes asked for twice at once are fetched once`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val callers = List(3) { async { graph.stops.refreshStopRoutesIfStale(s970) } }
        while (gateway.calls.isEmpty()) yield()
        gate.complete(Unit)
        assertEquals(1, callers.awaitAll().count { it == SyncOutcome.Synced })
        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `the weekly job and a sync-now overlapping make one catalog sync`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val weekly = async { graph.catalogSync.syncIfStale(Duration.ofDays(6)) }
        val now = async { graph.catalogSync.syncIfStale() }
        while (gateway.calls.size < 4) yield()
        gate.complete(Unit)
        assertEquals(setOf(SyncOutcome.Synced, SyncOutcome.UpToDate), setOf(weekly.await(), now.await()))
        assertEquals(4, gateway.calls.size)
    }

    @Test
    fun `a waiting caller still syncs when the one holding the lock is cancelled`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val first = async { graph.routes.refreshRouteIfStale(r326) }
        while (gateway.calls.isEmpty()) yield()
        val second = async { graph.routes.refreshRouteIfStale(r326) }
        yield()
        first.cancel()
        gate.complete(Unit)
        assertEquals(SyncOutcome.Synced, second.await())
        assertTrue(first.isCancelled)
        assertEquals(2, graph.db.routeDataDao().countPatterns(r326.value))
        assertEquals(false, graph.tracker.current(SyncKey.Route(r326)).inFlight)
    }

    @Test
    fun `a forced refresh during an ordinary one waits for it, then refetches`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val ordinary = async { graph.routes.refreshRouteIfStale(r326) }
        while (gateway.calls.isEmpty()) yield()
        val forced = async { graph.routes.refreshRoute(r326) }
        gate.complete(Unit)
        assertEquals(SyncOutcome.Synced, ordinary.await())
        assertEquals(SyncOutcome.Synced, forced.await())
        assertEquals(14, gateway.calls.size)
        assertEquals(2, graph.db.routeDataDao().countPatterns(r326.value))
    }
}
