package ge.hackerman.gza.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks each cached flow through fresh, stale, stale with an error, and back, with the clock
 * frozen exactly on the staleness boundaries (12 h for a route, 7 days for the catalog and a
 * stop's routes, 5 minutes of tolerance for a clock that moved back).
 */
@RunWith(AndroidJUnit4::class)
class CachedResultTransitionsTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val clock = graph.clock
    private val gateway = graph.gateway
    private val r326 = FixtureDomain.routeId("326")
    private val r301 = FixtureDomain.routeId("301")
    private val s970 = StopId(FixtureDomain.STOP_970)
    private val oneMilli = Duration.ofMillis(1)

    @After
    fun tearDown() = graph.db.close()

    private suspend fun route() = graph.routes.observeRoute(r326).first()

    private fun <T> CachedResult<T>.data(): CachedResult.Data<T> {
        assertTrue("expected data, got $this", this is CachedResult.Data)
        return this as CachedResult.Data<T>
    }

    @Test
    fun `a route is fresh until the last millisecond before 12 hours, then stale, then stale with the error`() =
        runBlocking {
            assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
            val syncedAt = clock.now
            val fresh = route().data()
            assertEquals(CachedResult.Data(fresh.value, syncedAt, Freshness.FRESH, null), fresh)

            clock.advanceBy(Duration.ofHours(12) - oneMilli)
            assertEquals(Freshness.FRESH, route().data().freshness)
            gateway.calls.clear()
            assertEquals(SyncOutcome.UpToDate, graph.routes.refreshRouteIfStale(r326))
            assertTrue(gateway.calls.isEmpty())

            clock.advanceBy(oneMilli)
            assertEquals(CachedResult.Data(fresh.value, syncedAt, Freshness.STALE, null), route())

            gateway.failure = TtcGatewayException.Network(IOException())
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))
            assertEquals(CachedResult.Data(fresh.value, syncedAt, Freshness.STALE, SyncError.OFFLINE), route())

            gateway.failure = null
            assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
            assertEquals(CachedResult.Data(fresh.value, clock.now, Freshness.FRESH, null), route())
        }

    @Test
    fun `a live collector sees stale, then the error appear, and never loading while a refresh runs`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        graph.routes.observeRoute(r326).test {
            val stale = awaitItem().data()
            assertEquals(Freshness.STALE, stale.freshness)
            assertEquals(null, stale.refreshError)

            val gate = CompletableDeferred<Unit>()
            gateway.gate = gate
            gateway.failure = TtcGatewayException.Network(IOException())
            gateway.calls.clear()
            val refresh = async { graph.routes.refreshRouteIfStale(r326) }
            while (gateway.calls.isEmpty()) yield()
            gate.complete(Unit)
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), refresh.await())

            var item = awaitItem().data()
            while (item.refreshError == null) item = awaitItem().data()
            assertEquals(CachedResult.Data(stale.value, stale.syncedAt, Freshness.STALE, SyncError.OFFLINE), item)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing cached goes loading, unavailable, loading again while retrying, then fresh`() = runBlocking {
        graph.routes.observeRoute(r301).test {
            assertEquals(CachedResult.Loading, awaitItem())

            gateway.failure = TtcGatewayException.Network(IOException())
            graph.routes.refreshRouteIfStale(r301)
            assertEquals(CachedResult.Unavailable(SyncError.OFFLINE), expectMostRecentItemSkippingLoading())

            val gate = CompletableDeferred<Unit>()
            gateway.gate = gate
            gateway.failure = null
            val retry = async { graph.routes.refreshRouteIfStale(r301) }
            assertEquals(CachedResult.Loading, awaitItem())
            gate.complete(Unit)
            assertEquals(SyncOutcome.Synced, retry.await())
            var item = awaitItem()
            while (item !is CachedResult.Data) item = awaitItem()
            assertEquals(Freshness.FRESH, item.freshness)
            assertEquals(null, item.refreshError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // While the first attempt runs, Loading repeats nothing (distinctUntilChanged), so the next
    // distinct item after it is the outcome.
    private suspend fun <T> ReceiveTurbine<CachedResult<T>>.expectMostRecentItemSkippingLoading(): CachedResult<T> {
        var item = awaitItem()
        while (item == CachedResult.Loading) item = awaitItem()
        return item
    }

    @Test
    fun `a dead key with nothing cached is unavailable with no key`() = runBlocking {
        gateway.failure = TtcGatewayException.Http(403, null)
        assertEquals(SyncOutcome.Failed(SyncError.NO_KEY), graph.routes.refreshRouteIfStale(r326))
        assertEquals(CachedResult.Unavailable(SyncError.NO_KEY), route())
    }

    @Test
    fun `the catalog is fresh for exactly 7 days less a millisecond`() = runBlocking {
        assertEquals(SyncOutcome.Synced, graph.catalogSync.syncIfStale())
        val syncedAt = clock.now

        clock.advanceBy(Duration.ofDays(7) - oneMilli)
        assertEquals(Freshness.FRESH, graph.stops.observeStops().first().data().freshness)
        assertEquals(Freshness.FRESH, graph.routes.observeRoutes().first().data().freshness)
        gateway.calls.clear()
        assertEquals(SyncOutcome.UpToDate, graph.catalogSync.syncIfStale())
        assertTrue(gateway.calls.isEmpty())

        clock.advanceBy(oneMilli)
        val stale = graph.stops.observeStops().first().data()
        assertEquals(Freshness.STALE, stale.freshness)
        assertEquals(syncedAt, stale.syncedAt)
        assertEquals(Freshness.STALE, graph.routes.observeRoutes().first().data().freshness)

        gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.catalogSync.syncIfStale())
        val offline = graph.stops.observeStops().first().data()
        assertEquals(Freshness.STALE, offline.freshness)
        assertEquals(SyncError.OFFLINE, offline.refreshError)
        assertEquals(2753, offline.value.size)
        assertEquals(SyncError.OFFLINE, graph.routes.observeRoutes().first().data().refreshError)
    }

    @Test
    fun `a stop's routes are fresh for 7 days less a millisecond`() = runBlocking {
        assertEquals(SyncOutcome.Synced, graph.stops.refreshStopRoutesIfStale(s970))
        clock.advanceBy(Duration.ofDays(7) - oneMilli)
        assertEquals(Freshness.FRESH, graph.stops.observeStopRoutes(s970).first().data().freshness)
        assertEquals(SyncOutcome.UpToDate, graph.stops.refreshStopRoutesIfStale(s970))
        clock.advanceBy(oneMilli)
        assertEquals(Freshness.STALE, graph.stops.observeStopRoutes(s970).first().data().freshness)
        gateway.failure = TtcGatewayException.Malformed(null)
        assertEquals(SyncOutcome.Failed(SyncError.MALFORMED), graph.stops.refreshStopRoutesIfStale(s970))
        val data = graph.stops.observeStopRoutes(s970).first().data()
        assertEquals(SyncError.MALFORMED, data.refreshError)
        assertTrue(data.value.isNotEmpty())
    }

    @Test
    fun `a clock moved back by exactly 5 minutes is tolerated, a millisecond more is stale and resyncs`() =
        runBlocking {
            graph.routes.refreshRouteIfStale(r326)
            val syncedAt = clock.now
            clock.now = syncedAt - Duration.ofMinutes(5)
            assertEquals(Freshness.FRESH, route().data().freshness)
            assertEquals(SyncOutcome.UpToDate, graph.routes.refreshRouteIfStale(r326))
            clock.now = syncedAt - Duration.ofMinutes(5) - oneMilli
            assertEquals(Freshness.STALE, route().data().freshness)
            assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
            assertEquals(Freshness.FRESH, route().data().freshness)
        }
}
