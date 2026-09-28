package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TBILISI_BOUNDS
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Per-element leniency must not turn a wrong body into an empty answer: an object where a
 * list belongs (or the reverse) is Malformed on every endpoint, so callers keep their cached
 * data instead of showing "no buses".
 */
class TtcGatewayClientShapeTest {
    private val gateway = FixtureGateway()
    private val stop = StopId("1:970")
    private val route = RouteId("1:R97493")
    private val patterns = listOf(PatternSuffix("0:01"))
    private val trip = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.694033, 44.801559))

    @AfterEach
    fun tearDown() = gateway.close()

    private val endpoints: Map<String, Pair<String, suspend TtcGatewayClient.() -> Any>> = mapOf(
        "stops" to (OBJECT to { stops(Language.EN) }),
        "stop" to (ARRAY to { stop(stop, Language.EN) }),
        "stopRoutes" to (OBJECT to { stopRoutes(stop, Language.EN) }),
        "arrivalBoard" to (OBJECT to { arrivalBoard(stop, Language.EN) }),
        "routes" to (OBJECT to { routes(Language.EN) }),
        "route" to (ARRAY to { route(route, Language.EN) }),
        "schedule" to (OBJECT to { schedule(route, patterns.single(), Language.EN) }),
        "patternStops" to (OBJECT to { patternStops(route, patterns.single(), Language.EN) }),
        "polylines" to (ARRAY to { polylines(route, patterns) }),
        "positions" to (ARRAY to { positions(route, patterns) }),
        "plan" to (ARRAY to { plan(trip, Language.EN) }),
        "geocode" to (ARRAY to { geocode("Rustaveli", Language.EN, TBILISI_BOUNDS) }),
        "reverseGeocode" to (ARRAY to { reverseGeocode(trip.from, Language.EN) })
    )

    @TestFactory
    fun `a body of the wrong top-level shape is malformed on every endpoint`(): List<DynamicTest> =
        endpoints.flatMap { (name, spec) ->
            val (wrong, call) = spec
            listOf(wrong, "null", "5").map { body ->
                DynamicTest.dynamicTest("$name <- $body") {
                    gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
                    val client = gateway.client()
                    assertFailsWith<TtcGatewayException.Malformed> { runBlocking { client.call() } }
                }
            }
        }

    private companion object {
        const val OBJECT = """{"items":[]}"""
        const val ARRAY = """[{"id":"1:970"}]"""
    }
}
