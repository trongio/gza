package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The error backoff seen from the syncs, not the policy: every kind of failure counts, the
 * window is measured from the last real attempt, a backed off call makes no request even when
 * the gateway is back, a success resets it, and a forced refresh never waits.
 */
@RunWith(AndroidJUnit4::class)
class ErrorBackoffAdversarialTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val clock = graph.clock
    private val r326 = FixtureDomain.routeId("326")
    private val routeKey = SyncKey.Route(r326)
    private val s970 = StopId(FixtureDomain.STOP_970)
    private val oneMilli = Duration.ofMillis(1)

    @After
    fun tearDown() = graph.db.close()

    private fun healGateway() {
        gateway.failure = null
        gateway.routeOverride = null
        gateway.stopsOverride = null
        gateway.routesOverride = null
        gateway.stopRoutesOverride = null
    }

    /** One way for a route sync to fail, and the error it must report. */
    private class Failure(val expected: SyncError, val apply: () -> Unit)

    private fun routeFailures() = listOf(
        Failure(SyncError.OFFLINE) { gateway.failure = TtcGatewayException.Network(IOException()) },
        Failure(SyncError.SERVER) { gateway.failure = TtcGatewayException.Http(HTTP_SERVER_ERROR, null) },
        Failure(SyncError.MALFORMED) { gateway.failure = TtcGatewayException.Malformed(null) },
        Failure(SyncError.EMPTY_OR_SHRUNK) {
            gateway.routeOverride = { id, language -> FixtureDomain.route(id, language).copy(patterns = emptyList()) }
        },
        Failure(SyncError.NO_KEY) { gateway.failure = TtcGatewayException.Http(HTTP_UNAUTHORIZED, null) },
        Failure(SyncError.OFFLINE) { gateway.failure = TtcGatewayException.Network(IOException()) },
        Failure(SyncError.SERVER) { gateway.failure = TtcGatewayException.Http(HTTP_BAD_GATEWAY, null) }
    )

    @Test
    fun `failures of every kind share one doubling window, each edge exact, with the gateway already back`() =
        runBlocking {
            assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
            clock.advanceBy(Duration.ofHours(13))
            val windows = listOf(5L, 10L, 20L, 40L, 80L, 120L, 120L).map { Duration.ofMinutes(it) }
            routeFailures().zip(windows).forEachIndexed { index, (failure, window) ->
                failure.apply()
                gateway.calls.clear()
                assertEquals(SyncOutcome.Failed(failure.expected), graph.routes.refreshRouteIfStale(r326))
                assertTrue("attempt ${index + 1} made no request", gateway.calls.isNotEmpty())
                assertEquals(index + 1, graph.tracker.current(routeKey).consecutiveFailures)

                // The gateway is healthy again, but the window is not over: same error, no request.
                healGateway()
                gateway.calls.clear()
                clock.advanceBy(window - oneMilli)
                assertEquals(SyncOutcome.Failed(failure.expected), graph.routes.refreshRouteIfStale(r326))
                graph.routeSync.refreshActiveRoutes()
                assertTrue("window ${index + 1} leaked ${gateway.calls}", gateway.calls.isEmpty())
                // A backed off answer is not an attempt: it neither counts nor moves the window.
                assertEquals(index + 1, graph.tracker.current(routeKey).consecutiveFailures)
                clock.advanceBy(oneMilli)
            }

            // The window is over and the gateway is back: one real attempt, and the count resets.
            assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
            assertEquals(0, graph.tracker.current(routeKey).consecutiveFailures)
            assertEquals(null, graph.tracker.current(routeKey).lastError)

            // After the reset the next failure waits 5 minutes again, not 2 hours.
            clock.advanceBy(Duration.ofHours(13))
            gateway.failure = TtcGatewayException.Network(IOException())
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))
            healGateway()
            clock.advanceBy(Duration.ofMinutes(5) - oneMilli)
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))
            clock.advanceBy(oneMilli)
            assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        }

    @Test
    fun `pull to refresh ignores the window, and its own failure extends it from its own time`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))

        // Still offline, one minute in: the user pulls; it tries (and fails) at once.
        clock.advanceBy(Duration.ofMinutes(1))
        gateway.calls.clear()
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRoute(r326))
        assertTrue(gateway.calls.isNotEmpty())
        assertEquals(2, graph.tracker.current(routeKey).consecutiveFailures)
        val pulledAt = clock.now

        // The second failure's window is 10 minutes from the pull, not from the first failure.
        healGateway()
        clock.now = pulledAt + Duration.ofMinutes(10) - oneMilli
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))

        // Pulling inside the window with the gateway back succeeds and ends the backoff.
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRoute(r326))
        assertEquals(0, graph.tracker.current(routeKey).consecutiveFailures)
        assertEquals(SyncOutcome.UpToDate, graph.routes.refreshRouteIfStale(r326))
    }

    @Test
    fun `a failed route does not back off a different route`() = runBlocking {
        val r301 = FixtureDomain.routeId("301")
        gateway.routeOverride = { id, language ->
            if (id == r326) throw TtcGatewayException.Network(IOException()) else FixtureDomain.route(id, language)
        }
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r301))
        assertEquals(1, graph.tracker.current(routeKey).consecutiveFailures)
        assertEquals(0, graph.tracker.current(SyncKey.Route(r301)).consecutiveFailures)
    }

    @Test
    fun `a clock moved back during the window retries at once`() = runBlocking {
        gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))
        healGateway()
        clock.advanceBy(Duration.ofMinutes(-1))
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
    }

    @Test
    fun `a retry cancelled mid flight does not restart the window`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.routes.refreshRouteIfStale(r326))

        // The window ends; the screen retries and is left before the gateway answers.
        healGateway()
        clock.advanceBy(Duration.ofMinutes(5))
        gateway.gate = CompletableDeferred()
        gateway.calls.clear()
        val retry = async { graph.routes.refreshRouteIfStale(r326) }
        while (gateway.calls.isEmpty()) yield()
        retry.cancel()
        retry.join()
        gateway.gate = null

        // Back on the screen a few seconds later, online: the cancelled attempt learned nothing.
        clock.advanceBy(Duration.ofSeconds(3))
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
    }

    @Test
    fun `stop routes count every failure kind and a success resets them`() = runBlocking {
        gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.stops.refreshStopRoutesIfStale(s970))
        clock.advanceBy(Duration.ofMinutes(5))
        gateway.failure = TtcGatewayException.Http(HTTP_SERVER_ERROR, null)
        assertEquals(SyncOutcome.Failed(SyncError.SERVER), graph.stops.refreshStopRoutesIfStale(s970))

        healGateway()
        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(10) - oneMilli)
        assertEquals(SyncOutcome.Failed(SyncError.SERVER), graph.stops.refreshStopRoutesIfStale(s970))
        assertTrue(gateway.calls.isEmpty())
        clock.advanceBy(oneMilli)
        assertEquals(SyncOutcome.Synced, graph.stops.refreshStopRoutesIfStale(s970))
        assertEquals(0, graph.tracker.current(SyncKey.StopRoutes(s970)).consecutiveFailures)
    }

    @Test
    fun `the catalog backs off each table on its own, and a shrunk list counts as a failure`() = runBlocking {
        // Stops time out, routes arrive.
        gateway.stopsOverride = { throw TtcGatewayException.Network(IOException()) }
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.catalogSync.syncIfStale(respectBackoff = true))
        assertEquals(280, graph.db.routeDao().count())
        assertEquals(0, graph.tracker.current(SyncKey.Routes).consecutiveFailures)

        // Within the stops window: no stops request, and routes are fresh, so no request at all.
        healGateway()
        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(5) - oneMilli)
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), graph.catalogSync.syncIfStale(respectBackoff = true))
        assertTrue(gateway.calls.isEmpty())
        clock.advanceBy(oneMilli)
        assertEquals(SyncOutcome.Synced, graph.catalogSync.syncIfStale(respectBackoff = true))
        assertEquals(2753, graph.db.stopDao().count())

        // A week later the gateway sends an empty stop list: kept cache, and a backoff all the same.
        clock.advanceBy(Duration.ofDays(8))
        gateway.stopsOverride = { emptyList() }
        assertEquals(
            SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK),
            graph.catalogSync.syncIfStale(respectBackoff = true)
        )
        assertEquals(2753, graph.db.stopDao().count())
        healGateway()
        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(4))
        assertEquals(
            SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK),
            graph.catalogSync.syncIfStale(respectBackoff = true)
        )
        assertTrue(gateway.calls.isEmpty())
        // The worker does not wait, and its success ends the backoff for app open too.
        assertEquals(SyncOutcome.Synced, graph.catalogSync.syncIfStale())
        assertEquals(0, graph.tracker.current(SyncKey.Stops).consecutiveFailures)
        assertEquals(SyncOutcome.UpToDate, graph.catalogSync.syncIfStale(respectBackoff = true))
    }

    @Test
    fun `the worker's failures grow the app open window too`() = runBlocking {
        gateway.failure = TtcGatewayException.Network(IOException())
        repeat(3) { graph.catalogSync.syncIfStale() }
        assertEquals(3, graph.tracker.current(SyncKey.Stops).consecutiveFailures)
        healGateway()
        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(20) - oneMilli)
        graph.catalogSync.syncIfStale(respectBackoff = true)
        assertTrue(gateway.calls.isEmpty())
        clock.advanceBy(oneMilli)
        assertEquals(SyncOutcome.Synced, graph.catalogSync.syncIfStale(respectBackoff = true))
        assertTrue(gateway.callsTo("stops ${Language.EN}").size == 1)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_SERVER_ERROR = 500
        const val HTTP_BAD_GATEWAY = 502
    }
}
