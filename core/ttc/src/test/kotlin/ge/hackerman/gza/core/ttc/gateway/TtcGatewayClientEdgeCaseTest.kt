package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.http.TtcGateway
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.Fixtures
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
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

    // Type drift (not nulls): one vehicle with a string heading must not empty its whole
    // pattern group, or the route looks like it has no buses in that direction.
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

    // Same drift in a list endpoint: one stop with a numeric code must not fail all 2,753 stops.
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

    @Test
    fun `one mistyped board row drops only itself`() {
        val rows = TtcJson.parseToJsonElement(Fixtures.text("terminus/551-20260928T2043/arrival-times.json")).jsonArray
        val drifted = buildJsonArray {
            add(rows[0])
            add(
                buildJsonObject {
                    rows[1].jsonObject.forEach { (k, v) -> put(k, v) }
                    put("realtime", "yes")
                }
            )
            add(rows[2])
        }
        gateway.respondWith { FixtureGateway.response(200, drifted.toString(), "application/json") }
        val board = runBlocking { gateway.client().arrivalBoard(stop970, Language.EN) }
        assertEquals(listOf("551", "326"), board.arrivals.map { it.routeShortName })
    }

    @Test
    fun `one mistyped itinerary drops only itself`() {
        val plan = TtcJson.parseToJsonElement(
            Fixtures.text("plan/leave-now-970-to-freedom-square-20260928T2043.json")
        ).jsonObject
        val itineraries = plan.getValue("itineraries").jsonArray
        val drifted = JsonObject(
            plan + (
                "itineraries" to buildJsonArray {
                    add(
                        buildJsonObject {
                            itineraries[0].jsonObject.forEach { (k, v) -> put(k, v) }
                            put("duration", "long")
                        }
                    )
                    add(itineraries[1])
                    add(itineraries[2])
                }
                )
        )
        gateway.respondWith { FixtureGateway.response(200, drifted.toString(), "application/json") }
        val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.694033, 44.801559))
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        val expected = listOf(1, 2).map { i ->
            OffsetDateTime.parse(itineraries[i].jsonObject.getValue("startTime").jsonPrimitive.content).toInstant()
        }
        assertEquals(expected, result.itineraries.map { it.start })
    }
}
