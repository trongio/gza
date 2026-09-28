package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.Fixtures
import ge.hackerman.gza.core.ttc.testing.JsonStep
import ge.hackerman.gza.core.ttc.testing.JsonVariants
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The edges of the all-misfit rule that the per-endpoint tests leave open: every list endpoint,
 * not only stops, reads `[]` and `[null]` as empty; service dates follow the nested rule like
 * stops do; and polyline keys count towards the rule.
 */
class AllMisfitEdgeTest {
    private val gateway = FixtureGateway()
    private val route551 = RouteId("1:minibusR24579")
    private val pattern = PatternSuffix("0:01")
    private val stop = StopId("1:970")
    private val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.6934, 44.8015))

    @AfterEach
    fun tearDown() = gateway.close()

    private fun serve(body: String) {
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
    }

    private fun <T> get(block: suspend TtcGatewayClient.() -> T): T = runBlocking { gateway.client().block() }

    private fun malformed(block: suspend TtcGatewayClient.() -> Unit) {
        assertFailsWith<TtcGatewayException.Malformed> { get(block) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["[]", "[null]", "[null,null]"])
    fun `every list endpoint reads nothing sent as empty, never malformed`(body: String) {
        serve(body)
        assertTrue(get { routes(Language.EN) }.isEmpty())
        assertTrue(get { stopRoutes(stop, Language.EN) }.isEmpty())
        assertTrue(get { arrivalBoard(stop, Language.EN) }.arrivals.isEmpty())
        assertTrue(get { schedule(route551, pattern, Language.EN) }.periods.isEmpty())
        assertTrue(get { patternStops(route551, pattern, Language.EN) }.stops.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "[null]"])
    fun `geocode and plan with nothing in their lists are empty`(raw: String) {
        serve("""{"type":"FeatureCollection","features":$raw}""")
        assertTrue(get { geocode("rustaveli", Language.EN) }.isEmpty())
        assertTrue(get { reverseGeocode(LatLon(0.0, 0.0), Language.EN) }.isEmpty())
        serve("""{"itineraries":$raw}""")
        assertTrue(get { plan(request, Language.EN) }.itineraries.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", """{"0:01":null}""", """{"0:01":null,"1:01":null}"""])
    fun `polylines with nothing in them are empty`(body: String) {
        serve(body)
        assertTrue(get { polylines(route551, listOf(pattern)) }.isEmpty())
    }

    @Test
    fun `polylines under keys that are not patterns are malformed, not a route without a line`() {
        serve("""{"01":{"color":"0033B4","encodedValue":"_p~iF~ps|U"}}""")
        malformed { polylines(route551, listOf(pattern)) }
    }

    @Test
    fun `one polyline under a bad key drops only itself`() {
        serve("""{"01":{"encodedValue":"_p~iF~ps|U"},"0:01":{"color":"0033B4","encodedValue":"_p~iF~ps|U"}}""")
        assertEquals(listOf(pattern), get { polylines(route551, listOf(pattern)) }.map { it.pattern })
    }

    @Test
    fun `a period whose service dates all changed format drops only that period`() {
        val original = TtcJson.parseToJsonElement(Fixtures.text("schedule/551-0-01-en.json"))
        val at = listOf(JsonStep.Index(0), JsonStep.Key("serviceDates"))
        serve(JsonVariants.set(original, at, TtcJson.parseToJsonElement("""["28/09/2026","29/09/2026"]""")).toString())
        val schedule = get { schedule(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size - 1, schedule.periods.size)
    }

    @Test
    fun `a schedule whose only period lost its service dates to a new format is malformed`() {
        serve("""[{"fromDay":"MONDAY","toDay":"FRIDAY","serviceDates":["28/09/2026"],"stops":[]}]""")
        malformed { schedule(route551, pattern, Language.EN) }
    }

    @Test
    fun `pattern stops that all serve other patterns are empty, not malformed`() {
        serve("""[{"stop":{"id":"1:970","lat":41.72,"lon":44.70},"patternSuffixes":["1:01"]}]""")
        assertTrue(get { patternStops(route551, pattern, Language.EN) }.stops.isEmpty())
    }
}
