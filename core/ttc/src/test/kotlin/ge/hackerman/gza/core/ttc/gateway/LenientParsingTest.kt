package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.gateway.dto.BoardArrivalDto
import ge.hackerman.gza.core.ttc.gateway.dto.PatternStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDetailDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.ServicePeriodDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.gateway.dto.decodeEachElement
import ge.hackerman.gza.core.ttc.gateway.dto.decodeObject
import ge.hackerman.gza.core.ttc.gateway.dto.toFeatureCollectionDto
import ge.hackerman.gza.core.ttc.gateway.dto.toPlanResponseDto
import ge.hackerman.gza.core.ttc.gateway.dto.toPolylineDtos
import ge.hackerman.gza.core.ttc.gateway.dto.toPositionDtos
import ge.hackerman.gza.core.ttc.gateway.mapper.toGeocodeResults
import ge.hackerman.gza.core.ttc.gateway.mapper.toPatternStops
import ge.hackerman.gza.core.ttc.gateway.mapper.toRouteDetailOrNull
import ge.hackerman.gza.core.ttc.gateway.mapper.toRoutePolylines
import ge.hackerman.gza.core.ttc.gateway.mapper.toRoutePositions
import ge.hackerman.gza.core.ttc.gateway.mapper.toRouteSchedule
import ge.hackerman.gza.core.ttc.gateway.mapper.toRoutes
import ge.hackerman.gza.core.ttc.gateway.mapper.toStopBoard
import ge.hackerman.gza.core.ttc.gateway.mapper.toStopOrNull
import ge.hackerman.gza.core.ttc.gateway.mapper.toStops
import ge.hackerman.gza.core.ttc.gateway.mapper.toTripPlan
import ge.hackerman.gza.core.ttc.testing.Fixtures
import ge.hackerman.gza.core.ttc.testing.JsonPath
import ge.hackerman.gza.core.ttc.testing.JsonVariants
import ge.hackerman.gza.core.ttc.testing.render
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Generated from the real fixtures: every mutation must decode with [TtcJson] and map without
 * throwing. A new null, a missing field, an unknown field or a new enum value may drop an
 * item, never a response, unless that item was the only one: then nothing usable is left of
 * what was sent, and the all-misfit rule rightly calls the response malformed.
 */
class LenientParsingTest {
    private class Root(
        val name: String,
        val fixture: String,
        /** For single-object roots: paths whose loss may legitimately make the result null. */
        val identityPaths: Set<String> = emptySet(),
        /** The only item of a response list: breaking it may make the response malformed. */
        val soleItems: Set<String> = emptySet(),
        val decodeAndMap: (String) -> Any?
    )

    private val at = Instant.parse("2026-09-28T16:43:32Z")
    private val route = RouteId("1:R97493")
    private val pattern = PatternSuffix("0:01")
    private val trip = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.694033, 44.801559))

    // The client's own decoders, so the mutations exercise exactly what ships.
    private fun parse(json: String): JsonElement = TtcJson.parseToJsonElement(json)

    private val roots = listOf(
        Root("stops", "stops/all-en.json") { parse(it).decodeEachElement<StopDto>().toStops() },
        Root("stop", "stop/1-970-en.json", setOf(".id", ".lat", ".lon")) {
            parse(it).decodeObject<StopDto>().toStopOrNull()
        },
        Root("stop routes", "stop-routes/1-970-en.json") {
            parse(it).decodeEachElement<RouteDto>().toRoutes()
        },
        Root("board", "terminus/551-20260928T2043/arrival-times.json") {
            parse(it).decodeEachElement<BoardArrivalDto>().toStopBoard(StopId("1:970"), at)
        },
        Root("routes", "routes/all-en.json") { parse(it).decodeEachElement<RouteDto>().toRoutes() },
        Root("route detail", "route/472-en.json", setOf(".id")) {
            parse(it).decodeObject<RouteDetailDto>().toRouteDetailOrNull()
        },
        Root("schedule", "schedule/551-0-01-en.json") {
            parse(it).decodeEachElement<ServicePeriodDto>().toRouteSchedule(route, pattern)
        },
        Root("stops of patterns", "stops-of-patterns/326-0-01-en.json") {
            parse(it).decodeEachElement<PatternStopDto>().toPatternStops(route, pattern)
        },
        Root("polylines", "polylines/326.json") {
            parse(it).toPolylineDtos().toRoutePolylines()
        },
        Root("positions", "terminus/551-20260928T2043/positions.json") {
            parse(it).toPositionDtos().toRoutePositions(route, at)
        },
        Root("plan", "plan/leave-now-970-to-freedom-square-20260928T2043.json") {
            parse(it).toPlanResponseDto().toTripPlan(trip)
        },
        Root("geocode", "geocode/rustaveli-en.json") {
            parse(it).toFeatureCollectionDto().toGeocodeResults()
        },
        Root("reverse geocode", "reverse-geocode/1-970-en.json", soleItems = setOf(".features[0]")) {
            parse(it).toFeatureCollectionDto().toGeocodeResults()
        }
    )

    @TestFactory
    fun `mutated real responses decode and map`(): List<DynamicNode> = roots.map { root ->
        val original = JsonVariants.trimArrays(TtcJson.parseToJsonElement(Fixtures.text(root.fixture)), MAX_ARRAY)
        val baseline = root.decodeAndMap(original.toString())
        assertNotNull(baseline, "baseline of ${root.name}")
        DynamicContainer.dynamicContainer(
            root.name,
            listOf(
                DynamicTest.dynamicTest("unknown field everywhere changes nothing") {
                    assertEquals(baseline, root.decodeAndMap(JsonVariants.addUnknownEverywhere(original).toString()))
                }
            ) + fieldVariants(root, original) + arrayVariants(root, original) + enumVariants(root, original)
        )
    }

    private fun fieldVariants(root: Root, original: JsonElement): List<DynamicTest> =
        JsonVariants.fieldPaths(original).flatMap { path ->
            listOf(
                DynamicTest.dynamicTest("null ${path.render()}") {
                    check(root, path, JsonVariants.set(original, path, JsonNull))
                },
                DynamicTest.dynamicTest("without ${path.render()}") {
                    check(root, path, JsonVariants.remove(original, path))
                }
            )
        }

    private fun arrayVariants(root: Root, original: JsonElement): List<DynamicTest> =
        JsonVariants.arrayPaths(original).map { path ->
            DynamicTest.dynamicTest("null element in ${path.render().ifEmpty { "root" }}") {
                check(root, path, JsonVariants.insertNull(original, path))
            }
        }

    private fun enumVariants(root: Root, original: JsonElement): List<DynamicTest> =
        JsonVariants.stringFieldPaths(original, ENUM_KEYS).map { path ->
            DynamicTest.dynamicTest("new value in ${path.render()}") {
                check(root, path, JsonVariants.set(original, path, JsonPrimitive("SOMETHING_NEW")))
            }
        }

    private fun check(root: Root, path: JsonPath, mutated: JsonElement) {
        val result = try {
            root.decodeAndMap(mutated.toString())
        } catch (_: SerializationException) {
            val rendered = path.render()
            assertTrue(root.soleItems.any { rendered.startsWith(it) }, "only ${root.soleItems} may fail ${root.name}")
            return
        }
        if (result == null) {
            assertTrue(path.render() in root.identityPaths, "only ${root.identityPaths} may null ${root.name}")
        }
    }

    private companion object {
        const val MAX_ARRAY = 3
        val ENUM_KEYS = setOf("mode", "vehicleMode", "fromDay", "toDay", "departMode", "relativeDirection", "type")
    }
}
