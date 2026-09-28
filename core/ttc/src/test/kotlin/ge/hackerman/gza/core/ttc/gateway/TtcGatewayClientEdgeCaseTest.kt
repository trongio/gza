package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.http.TtcGateway
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.Fixtures
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class TtcGatewayClientEdgeCaseTest {
    private val gateway = FixtureGateway()
    private val route326 = RouteId("1:R97493")
    private val stop970 = StopId("1:970")

    @AfterEach
    fun tearDown() = gateway.close()

    @Test
    fun `a rotated key after a 401 gets the data on the second request`() {
        val rotated = "sentinel-rotated-key"
        val wrongKey = Fixtures.meta("errors/wrong-key-401.txt")
        val board = Fixtures.meta("terminus/551-20260928T2043/arrival-times.json")
        gateway.respondWith { request ->
            if (request.headers[TtcGateway.API_KEY_HEADER] == rotated) {
                FixtureGateway.response(
                    board.status,
                    Fixtures.text("terminus/551-20260928T2043/arrival-times.json"),
                    board.contentType
                )
            } else {
                FixtureGateway.response(wrongKey.status, Fixtures.text("errors/wrong-key-401.txt"), null)
            }
        }
        val result = runBlocking {
            gateway.client(clockAt = board.recordedAt, rotatedKey = rotated).arrivalBoard(stop970, Language.EN)
        }
        assertEquals(listOf("551", "301", "326"), result.arrivals.map { it.routeShortName })
        assertEquals(0, result.arrivals.first().realtimeMinutesHint)
        assertEquals(
            listOf(FirebaseFixtures.GATEWAY_KEY, rotated),
            gateway.requests.map { it.headers[TtcGateway.API_KEY_HEADER] }
        )
    }

    @Test
    fun `an unknown pattern gives no vehicles, not an error`() {
        val meta = gateway.serve("errors/positions-unknown-pattern.json")
        val positions = runBlocking {
            gateway.client(clockAt = meta.recordedAt).positions(route326, listOf(PatternSuffix("9:99")))
        }
        assertTrue(positions.vehicles.isEmpty())
        assertEquals(meta.recordedAt, positions.fetchedAt)
    }

    @Test
    fun `a missing bbox surfaces the gateway's problem detail`() {
        gateway.serve("errors/geocode-missing-bbox-400.json")
        val e = assertFailsWith<TtcGatewayException.Http> {
            runBlocking { gateway.client().geocode("rustaveli", Language.EN) }
        }
        assertEquals(400, e.code)
        assertEquals("Required parameter 'bbox' is not present.", e.problem?.detail)
    }

    @Test
    fun `the arrival board asks with scheduled times included`() {
        gateway.serve("terminus/551-20260928T2043/arrival-times.json")
        runBlocking { gateway.client().arrivalBoard(stop970, Language.KA) }
        val url = gateway.single().url
        assertEquals("false", url.queryParameter("ignoreScheduledArrivalTimes"))
        assertEquals("ka", url.queryParameter("locale"))
    }

    // Type drift (not nulls): the plan accepts Malformed for a wrong type, but here nothing
    // fails: one vehicle with a string heading silently empties its whole pattern group, so
    // the route looks like it has no buses in that direction.
    @Disabled("bug: one mistyped vehicle drops every vehicle of its pattern, silently")
    @Test
    fun `one mistyped vehicle drops only itself`() {
        gateway.respondWith {
            FixtureGateway.response(
                200,
                """{"0:01":[{"vehicleId":"1:1","lat":41.7,"lon":44.7,"heading":"NaN"},""" +
                    """{"vehicleId":"1:2","lat":41.72,"lon":44.70,"heading":null,"nextStopId":null}]}""",
                "application/json"
            )
        }
        val positions = runBlocking { gateway.client().positions(route326, listOf(PatternSuffix("0:01"))) }
        assertEquals(listOf("1:2"), positions.vehicles.map { it.vehicleId.value })
    }

    // Same drift in a list endpoint: one stop with a numeric code makes all 2,753 stops Malformed.
    @Disabled("bug: one mistyped list element fails the whole list")
    @Test
    fun `one mistyped stop drops only itself`() {
        val good = Fixtures.text("stop/1-970-en.json")
        gateway.respondWith {
            FixtureGateway.response(
                200,
                """[$good,{"id":"1:971","code":971,"name":"B","lat":41.7,"lon":44.7}]""",
                "application/json"
            )
        }
        val stops = runBlocking { gateway.client().stops(Language.EN) }
        assertEquals(listOf(stop970), stops.map { it.id })
    }
}
