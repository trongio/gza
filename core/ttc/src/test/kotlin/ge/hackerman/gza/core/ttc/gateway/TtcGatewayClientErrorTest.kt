package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.config.GatewayConfigUnavailableException
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.Fixtures
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TtcGatewayClientErrorTest {
    private val gateway = FixtureGateway()
    private val stop = StopId("1:970")
    private val route = RouteId("1:R97493")
    private val seen = mutableListOf<TtcGatewayException>()

    @AfterEach
    fun tearDown() {
        gateway.close()
        // Whatever failed, nothing a log line could show may carry the key or a URL.
        seen.forEach { e ->
            val text = listOf(e.message.orEmpty(), e.toString())
            text.forEach {
                assertFalse(FirebaseFixtures.GATEWAY_KEY in it, "key in $it")
                assertFalse("://" in it || gateway.server.hostName in it, "url in $it")
            }
        }
    }

    private inline fun <reified T : TtcGatewayException> fails(noinline block: suspend () -> Unit): T =
        assertFailsWith<T> { runBlocking { block() } }.also { seen += it }

    @Test
    fun `unknown stop is a 500 without a problem`() {
        gateway.serve("errors/stop-unknown-500.txt")
        val e = fails<TtcGatewayException.Http> { gateway.client().stop(StopId("1:99999999"), Language.EN) }
        assertEquals(500, e.code)
        assertNull(e.problem)
    }

    @Test
    fun `missing parameter is a 400 with the problem detail`() {
        gateway.serve("errors/schedule-missing-pattern-400.json")
        val e = fails<TtcGatewayException.Http> {
            gateway.client().schedule(route, PatternSuffix("0:01"), Language.EN)
        }
        assertEquals(400, e.code)
        assertEquals("Bad Request", e.problem?.title)
        assertEquals("Required parameter 'patternSuffix' is not present.", e.problem?.detail)
    }

    @Test
    fun `a problem body over the cap keeps the status and drops the detail`() {
        val huge = """{"title":"Bad Request","detail":"${"x".repeat(8 * 1024)}"}"""
        gateway.respondWith { FixtureGateway.response(400, huge, "application/problem+json") }
        val e = fails<TtcGatewayException.Http> { gateway.client().stopRoutes(stop, Language.EN) }
        assertEquals(400, e.code)
        assertNull(e.problem)
    }

    @Test
    fun `a problem body just under the cap is read whole`() {
        val detail = "y".repeat(4000)
        val body = """{"title":"Bad Request","detail":"$detail"}"""
        gateway.respondWith { FixtureGateway.response(400, body, "application/problem+json") }
        val e = fails<TtcGatewayException.Http> { gateway.client().stopRoutes(stop, Language.EN) }
        assertEquals(detail, e.problem?.detail)
    }

    @Test
    fun `plain text 400 has no problem`() {
        gateway.serve("errors/plan-departat-without-date-400.txt")
        val e = fails<TtcGatewayException.Http> { gateway.client().stopRoutes(stop, Language.EN) }
        assertEquals(400, e.code)
        assertNull(e.problem)
    }

    @Test
    fun `a second 401 after a rotated key surfaces as 401 after exactly two requests`() {
        gateway.serve("errors/wrong-key-401.txt")
        val e = fails<TtcGatewayException.Http> {
            gateway.client(rotatedKey = "sentinel-rotated-key").stops(Language.EN)
        }
        assertEquals(401, e.code)
        assertEquals(2, gateway.requests.size)
        assertEquals("sentinel-rotated-key", gateway.requests[1].headers["x-api-key"])
    }

    @Test
    fun `a redirect is returned, not followed`() {
        gateway.respondWith {
            MockResponse.Builder().code(302).addHeader("Location", gateway.server.url("/elsewhere")).build()
        }
        val e = fails<TtcGatewayException.Http> { gateway.client().stops(Language.EN) }
        assertEquals(302, e.code)
        assertEquals(1, gateway.requests.size)
    }

    @Test
    fun `a dropped connection is a network failure`() {
        gateway.respondWith { MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build() }
        fails<TtcGatewayException.Network> { gateway.client().stops(Language.EN) }
    }

    @Test
    fun `no key anywhere is NoKey and nothing is sent`() {
        val client = gateway.client(failure = GatewayConfigUnavailableException("no key"))
        val e = fails<TtcGatewayException.NoKey> { client.stops(Language.EN) }
        assertIs<GatewayConfigUnavailableException>(e.cause)
        assertEquals(0, gateway.server.requestCount)
    }

    // A list with items none of which fit is a changed gateway, not an empty one.
    @ParameterizedTest
    @ValueSource(
        strings = ["not json", "", "null", """{"id":5}""", "\"stops\"", "5", "[1,2]", """[{"id":{}}]""", "[null,7]"]
    )
    fun `a 200 that is not the expected shape is malformed`(body: String) {
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
        fails<TtcGatewayException.Malformed> { gateway.client().stops(Language.EN) }
    }

    // Empty, or only nulls, is what the gateway really sent (the gondola board sends []).
    @ParameterizedTest
    @ValueSource(strings = ["[null]", "[]"])
    fun `a list with nothing in it is empty, not malformed`(body: String) {
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
        assertTrue(runBlocking { gateway.client().stops(Language.EN) }.isEmpty())
    }

    @Test
    fun `a 204 is malformed`() {
        gateway.respondWith { MockResponse.Builder().code(204).build() }
        fails<TtcGatewayException.Malformed> { gateway.client().stop(stop, Language.EN) }
    }

    @Test
    fun `a stop or route without its identity is malformed`() {
        gateway.respondWith { FixtureGateway.response(200, """{"id":"970","name":"x"}""", "application/json") }
        fails<TtcGatewayException.Malformed> { gateway.client().stop(stop, Language.EN) }
        fails<TtcGatewayException.Malformed> { gateway.client().route(route, Language.EN) }
    }

    @Test
    fun `a null element in a list drops only that element`() {
        val body = "[null," + Fixtures.text("stop/1-970-en.json") + "]"
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
        assertEquals(listOf(stop), runBlocking { gateway.client().stops(Language.EN) }.map { it.id })
    }

    @Test
    fun `a pattern whose vehicles all misfit fails the whole positions response`() {
        val body = """{"0:01":[{"vehicleId":"1:1","lat":41.7,"lon":44.7}],"1:01":[{"vehicleId":"1:2","lat":"north"}]}"""
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
        fails<TtcGatewayException.Malformed> {
            gateway.client().positions(route, listOf(PatternSuffix("0:01"), PatternSuffix("1:01")))
        }
    }

    @Test
    fun `a pattern with no vehicles, or only nulls, is not malformed`() {
        val body = """{"0:01":[{"vehicleId":"1:1","lat":41.7,"lon":44.7}],"1:01":[],"2:01":[null]}"""
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
        val positions = runBlocking {
            gateway.client().positions(route, listOf(PatternSuffix("0:01"), PatternSuffix("1:01")))
        }
        assertEquals(1, positions.vehicles.size)
    }

    @Test
    fun `an empty pattern list is a programming error and sends nothing`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { gateway.client().positions(route, emptyList()) } }
        assertFailsWith<IllegalArgumentException> { runBlocking { gateway.client().polylines(route, emptyList()) } }
        assertEquals(0, gateway.server.requestCount)
    }

    @Test
    fun `cancellation stays cancellation`() {
        gateway.respondWith {
            MockResponse.Builder().headersDelay(5, TimeUnit.SECONDS).body("[]").build()
        }
        val client = gateway.client()
        var thrown: Throwable? = null
        runBlocking {
            val job = launch(Dispatchers.Default) {
                try {
                    client.stops(Language.EN)
                } catch (e: Throwable) {
                    thrown = e
                    throw e
                }
            }
            delay(100)
            job.cancelAndJoin()
        }
        assertIs<CancellationException>(thrown)
        assertTrue(thrown !is TtcGatewayException)
    }
}
