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
import ge.hackerman.gza.core.ttc.testing.JsonPath
import ge.hackerman.gza.core.ttc.testing.JsonStep
import ge.hackerman.gza.core.ttc.testing.JsonVariants
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * The likeliest real shape change is a rename. Every DTO field is optional and unknown keys
 * are ignored, so renamed items decode as all-null DTOs and are then dropped by the mapper.
 * When every item goes that way the gateway changed, and returning an empty list reads as
 * "nothing there", which is what the all-misfit rule exists to prevent. Nested lists follow
 * the same rule by dropping their parent.
 */
class RenamedFieldsTest {
    private val gateway = FixtureGateway()
    private val route551 = RouteId("1:minibusR24579")
    private val pattern = PatternSuffix("0:01")
    private val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.6934, 44.8015))
    private val plan = "plan/leave-now-970-to-freedom-square-20260928T2101.json"

    @AfterEach
    fun tearDown() = gateway.close()

    private fun serve(body: String) {
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
    }

    /** Serves [fixture] with [raw] put at [at]; returns the untouched fixture. */
    private fun serve(fixture: String, at: JsonPath, raw: String): JsonElement {
        val original = TtcJson.parseToJsonElement(Fixtures.text(fixture))
        serve(JsonVariants.set(original, at, TtcJson.parseToJsonElement(raw)).toString())
        return original
    }

    private fun path(vararg steps: Any): JsonPath = steps.map {
        if (it is Int) JsonStep.Index(it) else JsonStep.Key(it as String)
    }

    private fun malformed(block: suspend () -> Unit) {
        assertFailsWith<TtcGatewayException.Malformed> { runBlocking { block() } }
    }

    @Test
    fun `stops whose fields were all renamed are malformed, not empty`() {
        serve("""[{"stopId":"1:970","latitude":41.72,"longitude":44.70},{"stopId":"1:969"}]""")
        malformed { gateway.client().stops(Language.EN) }
    }

    @Test
    fun `vehicles whose fields were all renamed are malformed, not an empty road`() {
        serve("""{"0:01":[{"id":"1:1","latitude":41.7,"longitude":44.7}]}""")
        malformed { gateway.client().positions(RouteId("1:R97493"), listOf(PatternSuffix("0:01"))) }
    }

    @Test
    fun `a board whose arrivals were all renamed is malformed, not empty`() {
        serve("""[{"route":"326","minutes":3},{"route":"301","minutes":7}]""")
        malformed { gateway.client().arrivalBoard(StopId("1:970"), Language.EN) }
    }

    @Test
    fun `routes and stop routes whose fields were all renamed are malformed`() {
        serve("""[{"routeId":"1:R97493","name":"326"}]""")
        malformed { gateway.client().routes(Language.EN) }
        malformed { gateway.client().stopRoutes(StopId("1:970"), Language.EN) }
    }

    @Test
    fun `a route whose patterns were all renamed is malformed, not a route without patterns`() {
        serve("""{"id":"1:minibusR24579","shortName":"551","patterns":[{"suffix":"0:01","direction":0}]}""")
        malformed { gateway.client().route(route551, Language.EN) }
    }

    @Test
    fun `a plan whose itineraries were all renamed is malformed, not no way there`() {
        serve("""{"from":{"lat":41.72,"lon":44.70},"itineraries":[{"begin":"2026-09-28T17:02:09Z","segments":[]}]}""")
        malformed { gateway.client().plan(request, Language.EN) }
    }

    @Test
    fun `a schedule whose periods lost all their stops to renames is malformed`() {
        serve("""[{"fromDay":"MONDAY","toDay":"FRIDAY","serviceDates":["2026-09-28"],"stops":[{"stopId":"1:970"}]}]""")
        malformed { gateway.client().schedule(route551, pattern, Language.EN) }
    }

    @Test
    fun `a schedule period whose stops were all renamed drops only that period`() {
        val original = serve("schedule/551-0-01-en.json", path(0, "stops"), """[{"stopId":"1:970","times":"7:00"}]""")
        val schedule = runBlocking { gateway.client().schedule(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size - 1, schedule.periods.size)
    }

    @Test
    fun `pattern stops whose stops were all renamed are malformed`() {
        serve("""[{"station":{"id":"1:970"},"patternSuffixes":["0:01"]}]""")
        malformed { gateway.client().patternStops(route551, pattern, Language.EN) }
    }

    @Test
    fun `polylines whose fields were all renamed are malformed`() {
        serve("""{"0:01":{"colour":"0033B4","points":"_p~iF~ps|U"}}""")
        malformed { gateway.client().polylines(route551, listOf(pattern)) }
    }

    @Test
    fun `geocode features whose fields were all renamed are malformed, not no results`() {
        serve("""{"type":"FeatureCollection","features":[{"type":"Feature","geom":{"coords":[44.7,41.7]}}]}""")
        malformed { gateway.client().geocode("rustaveli", Language.EN) }
        malformed { gateway.client().reverseGeocode(LatLon(41.7, 44.7), Language.EN) }
    }

    @Test
    fun `a leg whose intermediate stops were all renamed drops only its itinerary`() {
        val original = serve(plan, path("itineraries", 0, "legs", 0, "intermediateStops"), """[{"stopId":"1:1"}]""")
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(original.jsonObject.getValue("itineraries").jsonArray.size - 1, result.itineraries.size)
    }

    @Test
    fun `a walk leg whose steps were all renamed drops only its itinerary`() {
        val original = serve(plan, path("itineraries", 0, "legs", 2, "steps"), """[{"direction":"LEFT"}]""")
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(original.jsonObject.getValue("itineraries").jsonArray.size - 1, result.itineraries.size)
    }
}
