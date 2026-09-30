package ge.hackerman.gza.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Everything a live collector sees while nothing goes wrong: an up to date refresh (including
 * one that records a use, and app open's catalog check) must never show an error, `Loading` or
 * `Unavailable`, not even for one emission. Also, one table failing must not flag the other.
 */
@RunWith(AndroidJUnit4::class)
class NoErrorFlashTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val clock = graph.clock
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val r326 = FixtureDomain.routeId("326")
    private val s970 = StopId(FixtureDomain.STOP_970)

    @After
    fun tearDown() {
        collectors.cancel()
        graph.db.close()
    }

    /** Every item [flow] emits from now on, collected on another thread. */
    private fun <T> record(flow: Flow<CachedResult<T>>): List<CachedResult<T>> {
        val items = CopyOnWriteArrayList<CachedResult<T>>()
        collectors.launch { flow.collect { items += it } }
        return items
    }

    private suspend fun awaitFirst(vararg recorded: List<*>) = withTimeout(TIMEOUT_MS) {
        while (recorded.any { it.isEmpty() }) delay(POLL_MS)
    }

    private fun assertOnlyFreshData(name: String, items: List<CachedResult<*>>) {
        assertTrue("$name emitted nothing", items.isNotEmpty())
        items.forEach {
            assertTrue("$name flashed $it", it is CachedResult.Data<*>)
            val data = it as CachedResult.Data<*>
            assertEquals("$name flashed an error: $items", null, data.refreshError)
            assertEquals("$name flashed stale: $items", Freshness.FRESH, data.freshness)
        }
    }

    @Test
    fun `up to date refreshes of every kind never flash an error, loading or stale`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.routes.refreshRouteIfStale(r326)
        graph.stops.refreshStopRoutesIfStale(s970)

        val route = record(graph.routes.observeRoute(r326))
        val routes = record(graph.routes.observeRoutes())
        val stops = record(graph.stops.observeStops())
        val stopRoutes = record(graph.stops.observeStopRoutes(s970))
        awaitFirst(route, routes, stops, stopRoutes)

        gateway.calls.clear()
        repeat(3) { assertEquals(SyncOutcome.UpToDate, graph.routes.refreshRouteIfStale(r326)) }
        // An hour later the use is written to sync_state, which every one of these flows reads.
        clock.advanceBy(Duration.ofMinutes(61))
        assertEquals(SyncOutcome.UpToDate, graph.routes.refreshRouteIfStale(r326))
        assertEquals(SyncOutcome.UpToDate, graph.stops.refreshStopRoutesIfStale(s970))
        assertEquals(SyncOutcome.UpToDate, graph.catalogSync.syncIfStale(respectBackoff = true))
        graph.routeSync.refreshActiveRoutes()
        delay(SETTLE_MS)
        assertTrue(gateway.calls.toString(), gateway.calls.isEmpty())

        assertOnlyFreshData("route", route)
        assertOnlyFreshData("routes", routes)
        assertOnlyFreshData("stops", stops)
        assertOnlyFreshData("stop routes", stopRoutes)
    }

    @Test
    fun `a successful refresh of a stale route shows stale then fresh, never an error or loading`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.routes.refreshRouteIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        val route = record(graph.routes.observeRoute(r326))
        awaitFirst(route)
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        withTimeout(TIMEOUT_MS) {
            while ((route.last() as? CachedResult.Data)?.freshness != Freshness.FRESH) delay(POLL_MS)
        }
        route.forEach {
            assertTrue("flashed $it", it is CachedResult.Data)
            assertEquals("flashed an error: $route", null, (it as CachedResult.Data).refreshError)
        }
    }

    @Test
    fun `routes failing does not flag the stops that synced`() = runBlocking {
        graph.catalogSync.syncIfStale()
        clock.advanceBy(Duration.ofDays(8))
        val stops = record(graph.stops.observeStops())
        val routes = record(graph.routes.observeRoutes())
        awaitFirst(stops, routes)
        gateway.routesOverride = { throw TtcGatewayException.Network(IOException()) }
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.catalogSync.syncIfStale(respectBackoff = true))
        withTimeout(TIMEOUT_MS) {
            while ((routes.last() as CachedResult.Data).refreshError == null) delay(POLL_MS)
            while ((stops.last() as CachedResult.Data).freshness != Freshness.FRESH) delay(POLL_MS)
        }
        stops.forEach { assertEquals("stops flashed an error: $stops", null, (it as CachedResult.Data).refreshError) }
    }

    @Test
    fun `answers from inside the backoff window emit nothing new`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.routes.refreshRouteIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.failure = TtcGatewayException.Network(IOException())
        graph.routes.refreshRouteIfStale(r326)
        val route = record(graph.routes.observeRoute(r326))
        awaitFirst(route)
        delay(SETTLE_MS)
        val before = route.toList()
        gateway.failure = null
        repeat(5) {
            clock.advanceBy(Duration.ofSeconds(30))
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))
        }
        delay(SETTLE_MS)
        assertEquals(before, route.toList())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 10L
        const val SETTLE_MS = 300L
    }
}
