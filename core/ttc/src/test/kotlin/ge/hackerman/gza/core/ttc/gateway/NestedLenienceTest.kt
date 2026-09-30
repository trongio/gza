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
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Real fixtures with one nested item mistyped: it drops only itself. When every item of a
 * nested list is mistyped its parent drops, and when every parent drops the response is
 * Malformed.
 */
class NestedLenienceTest {
    private val gateway = FixtureGateway()
    private val route551 = RouteId("1:minibusR24579")
    private val pattern = PatternSuffix("0:01")
    private val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.6934, 44.8015))

    @AfterEach
    fun tearDown() = gateway.close()

    private fun serve(body: JsonElement) {
        gateway.respondWith { FixtureGateway.response(200, body.toString(), "application/json") }
    }

    private fun fixture(path: String): JsonElement = TtcJson.parseToJsonElement(Fixtures.text(path))

    private fun path(vararg steps: Any): JsonPath = steps.map {
        if (it is Int) JsonStep.Index(it) else JsonStep.Key(it as String)
    }

    /** Sets [key] to [value] in every object of the array at [arrayPath]. */
    private fun mistypeAll(root: JsonElement, arrayPath: JsonPath, key: String, value: JsonPrimitive): JsonElement {
        val array = JsonVariants.get(root, arrayPath)!!.jsonArray
        val broken = JsonArray(array.map { JsonObject(it.jsonObject + (key to value)) })
        return JsonVariants.set(root, arrayPath, broken)
    }

    private fun malformed(block: suspend () -> Unit) {
        assertFailsWith<TtcGatewayException.Malformed> { runBlocking { block() } }
    }

    // Route patterns.

    @Test
    fun `one mistyped pattern drops only itself`() {
        serve(JsonVariants.set(fixture("route/551-en.json"), path("patterns", 0, "directionId"), JsonPrimitive("zero")))
        val route = runBlocking { gateway.client().route(route551, Language.EN) }
        assertEquals(listOf(PatternSuffix("1:01")), route.patterns.map { it.suffix })
    }

    @Test
    fun `a route whose patterns all misfit is malformed`() {
        serve(mistypeAll(fixture("route/551-en.json"), path("patterns"), "directionId", JsonPrimitive("zero")))
        malformed { gateway.client().route(route551, Language.EN) }
    }

    // Schedule period stops.

    @Test
    fun `one mistyped scheduled stop drops only itself and keeps the others' positions`() {
        val original = fixture("schedule/551-0-01-en.json")
        serve(JsonVariants.set(original, path(0, "stops", 0, "position"), JsonPrimitive("first")))
        val schedule = runBlocking { gateway.client().schedule(route551, pattern, Language.EN) }
        val originalStops = JsonVariants.get(original, path(0, "stops"))!!.jsonArray
        assertEquals(original.jsonArray.size, schedule.periods.size)
        val stops = schedule.periods.first().stops
        assertEquals(originalStops.size - 1, stops.size)
        assertEquals(2, stops.first().position)
        assertEquals(originalStops.size, stops.last().position)
    }

    @Test
    fun `a period whose stops all misfit drops, the other periods stay`() {
        val original = fixture("schedule/551-0-01-en.json")
        serve(mistypeAll(original, path(0, "stops"), "position", JsonPrimitive("first")))
        val schedule = runBlocking { gateway.client().schedule(route551, pattern, Language.EN) }
        assertEquals(original.jsonArray.size - 1, schedule.periods.size)
    }

    @Test
    fun `a schedule whose periods all misfit is malformed`() {
        var body = fixture("schedule/551-0-01-en.json")
        body.jsonArray.indices.forEach {
            body = mistypeAll(body, path(it, "stops"), "position", JsonPrimitive("first"))
        }
        serve(body)
        malformed { gateway.client().schedule(route551, pattern, Language.EN) }
    }

    // Plan steps and intermediate stops.

    private val plan = "plan/leave-now-970-to-freedom-square-20260928T2101.json"

    @Test
    fun `one mistyped walking step drops only itself`() {
        val original = fixture(plan)
        serve(
            JsonVariants.set(original, path("itineraries", 0, "legs", 2, "steps", 0, "distance"), JsonPrimitive("far"))
        )
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(original.jsonObject.getValue("itineraries").jsonArray.size, result.itineraries.size)
        val walk = result.itineraries.first().legs[2] as WalkLeg
        assertEquals(4, walk.steps.size)
    }

    @Test
    fun `an itinerary whose steps all misfit on one leg drops, the others stay`() {
        val original = fixture(plan)
        serve(mistypeAll(original, path("itineraries", 0, "legs", 2, "steps"), "distance", JsonPrimitive("far")))
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(original.jsonObject.getValue("itineraries").jsonArray.size - 1, result.itineraries.size)
    }

    @Test
    fun `one mistyped intermediate stop drops only itself`() {
        serve(
            JsonVariants.set(
                fixture(plan),
                path("itineraries", 0, "legs", 0, "intermediateStops", 0, "lat"),
                JsonPrimitive("x")
            )
        )
        val result = runBlocking { gateway.client().plan(request, Language.EN) }
        assertEquals(16, (result.itineraries.first().legs[0] as TransitLeg).intermediateStops.size)
    }

    @Test
    fun `a plan whose itineraries all misfit is malformed`() {
        var body = fixture(plan)
        body.jsonObject.getValue("itineraries").jsonArray.indices.forEach {
            body = JsonVariants.set(body, path("itineraries", it, "legs", 0, "distance"), JsonPrimitive("far"))
        }
        serve(body)
        malformed { gateway.client().plan(request, Language.EN) }
    }

    // Pattern stops.

    @Test
    fun `a mistyped pattern suffix drops only itself`() {
        val body = JsonVariants.set(
            fixture("stops-of-patterns/551-0-01-en.json"),
            path(0, "patternSuffixes"),
            JsonArray(listOf(JsonObject(emptyMap()), JsonPrimitive("0:01")))
        )
        serve(body)
        val stops = runBlocking { gateway.client().patternStops(route551, pattern, Language.EN) }
        assertEquals(StopId("1:970"), stops.stops.first().id)
    }
}
