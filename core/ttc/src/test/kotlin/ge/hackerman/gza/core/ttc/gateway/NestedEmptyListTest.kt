package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransitLeg
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.WalkLeg
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.Fixtures
import ge.hackerman.gza.core.ttc.testing.JsonPath
import ge.hackerman.gza.core.ttc.testing.JsonStep
import ge.hackerman.gza.core.ttc.testing.JsonVariants
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Every nested lenient list, sent as `null`, `[]` or `[null]`, is an empty list: the gateway
 * sent nothing there, which is not a changed shape, so its parent stays and nothing is Malformed.
 */
class NestedEmptyListTest {
    private val gateway = FixtureGateway()
    private val route551 = RouteId("1:minibusR24579")
    private val pattern = PatternSuffix("0:01")
    private val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.6934, 44.8015))
    private val plan = "plan/leave-now-970-to-freedom-square-20260928T2101.json"

    @AfterEach
    fun tearDown() = gateway.close()

    private fun fixture(path: String): JsonElement = TtcJson.parseToJsonElement(Fixtures.text(path))

    private fun path(vararg steps: Any): JsonPath = steps.map {
        if (it is Int) JsonStep.Index(it) else JsonStep.Key(it as String)
    }

    private fun serve(fixture: String, at: JsonPath, raw: String): JsonElement {
        val original = fixture(fixture)
        val body = JsonVariants.set(original, at, TtcJson.parseToJsonElement(raw))
        gateway.respondWith { FixtureGateway.response(200, body.toString(), "application/json") }
        return original
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "[null]"])
    fun `route patterns with nothing in them are no patterns`(raw: String) {
        serve("route/551-en.json", path("patterns"), raw)
        val route = runBlocking { gateway.client().route(route551, Language.EN) }
        assertEquals(route551, route.id)
        assertTrue(route.patterns.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "[null]"])
    fun `a schedule period with nothing in its stops keeps the period`(raw: String) {
        val original = serve("schedule/551-0-01-en.json", path(0, "stops"), raw)
        val schedule = runBlocking { gateway.client().schedule(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size, schedule.periods.size)
        assertTrue(schedule.periods.first().stops.isEmpty())
        assertTrue(schedule.periods.first().serviceDates.isNotEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "[null]"])
    fun `a schedule period with nothing in its service dates keeps the period`(raw: String) {
        val original = serve("schedule/551-0-01-en.json", path(0, "serviceDates"), raw)
        val schedule = runBlocking { gateway.client().schedule(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size, schedule.periods.size)
        assertTrue(schedule.periods.first().serviceDates.isEmpty())
        assertTrue(schedule.periods.first().stops.isNotEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "[null]"])
    fun `a walk leg with nothing in its steps keeps the itinerary`(raw: String) {
        val original = serve(plan, path("itineraries", 0, "legs", 2, "steps"), raw)
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(original.jsonObject.getValue("itineraries").jsonArray.size, result.itineraries.size)
        assertTrue((result.itineraries.first().legs[2] as WalkLeg).steps.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "[]", "[null]"])
    fun `a transit leg with nothing in its intermediate stops keeps the itinerary`(raw: String) {
        val original = serve(plan, path("itineraries", 0, "legs", 0, "intermediateStops"), raw)
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(original.jsonObject.getValue("itineraries").jsonArray.size, result.itineraries.size)
        assertTrue((result.itineraries.first().legs[0] as TransitLeg).intermediateStops.isEmpty())
    }

    // A null list says nothing, so the stop is kept; an empty one says it serves no pattern
    // asked for, so only that stop drops. Neither is Malformed.

    @ParameterizedTest
    @ValueSource(strings = ["null"])
    fun `a pattern stop without its suffix list is kept`(raw: String) {
        val original = serve("stops-of-patterns/551-0-01-en.json", path(0, "patternSuffixes"), raw)
        val stops = runBlocking { gateway.client().patternStops(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size, stops.stops.size)
        assertEquals(StopId("1:970"), stops.stops.first().id)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[]", "[null]"])
    fun `a pattern stop with nothing in its suffix list drops only itself`(raw: String) {
        val original = serve("stops-of-patterns/551-0-01-en.json", path(0, "patternSuffixes"), raw)
        val stops = runBlocking { gateway.client().patternStops(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size - 1, stops.stops.size)
        assertEquals(StopId("1:969"), stops.stops.first().id)
    }
}
