package ge.hackerman.gza.core.data.testing

import ge.hackerman.gza.core.model.BoundingBox
import ge.hackerman.gza.core.model.GeocodeResult
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TripPlan
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/**
 * Serves the recorded fixtures as domain objects ([FixtureDomain]). Every endpoint can be
 * overridden, [failure] makes every call throw, [gate] holds calls open, and [calls] and
 * [maxInFlight] count what was asked.
 */
@Suppress("TooManyFunctions") // Mirrors TtcGatewayClient.
internal class FakeTtcGatewayClient : TtcGatewayClient {
    @Volatile var failure: TtcGatewayException? = null

    /** When set, calls wait for it before answering. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    @Volatile var stopsOverride: (suspend (Language) -> List<Stop>)? = null

    @Volatile var routesOverride: (suspend (Language) -> List<Route>)? = null

    @Volatile var stopRoutesOverride: (suspend (StopId) -> List<Route>)? = null

    @Volatile var routeOverride: (suspend (RouteId, Language) -> RouteDetail)? = null

    @Volatile var patternStopsOverride: (suspend (RouteId, PatternSuffix) -> PatternStops)? = null

    @Volatile var scheduleOverride: (suspend (RouteId, PatternSuffix) -> RouteSchedule)? = null

    @Volatile var polylinesOverride: (suspend (RouteId, List<PatternSuffix>) -> List<RoutePolyline>)? = null

    val calls: MutableList<String> = CopyOnWriteArrayList()
    private val inFlight = AtomicInteger()
    private val maxInFlightCounter = AtomicInteger()
    val maxInFlight: Int get() = maxInFlightCounter.get()

    /** Calls whose description starts with [prefix]. */
    fun callsTo(prefix: String): List<String> = calls.filter { it.startsWith(prefix) }

    private suspend fun <T> call(description: String, answer: suspend () -> T): T {
        calls += description
        maxInFlightCounter.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
        try {
            gate?.await()
            failure?.let { throw it }
            return answer()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    override suspend fun stops(language: Language): List<Stop> =
        call("stops $language") { stopsOverride?.invoke(language) ?: FixtureDomain.stops(language) }

    override suspend fun stop(id: StopId, language: Language): Stop = error("not used by the data layer")

    override suspend fun stopRoutes(id: StopId, language: Language): List<Route> =
        call("stop-routes ${id.value} $language") { stopRoutesOverride?.invoke(id) ?: FixtureDomain.stopRoutes(id) }

    override suspend fun arrivalBoard(id: StopId, language: Language): StopBoard = error("not used by the data layer")

    override suspend fun routes(language: Language): List<Route> =
        call("routes $language") { routesOverride?.invoke(language) ?: FixtureDomain.routes(language) }

    override suspend fun route(id: RouteId, language: Language): RouteDetail = call("route ${id.value} $language") {
        routeOverride?.invoke(id, language) ?: FixtureDomain.route(id, language)
    }

    override suspend fun schedule(id: RouteId, pattern: PatternSuffix, language: Language): RouteSchedule =
        call("schedule ${id.value} ${pattern.value} $language") {
            scheduleOverride?.invoke(id, pattern) ?: FixtureDomain.schedule(id, pattern)
        }

    override suspend fun patternStops(id: RouteId, pattern: PatternSuffix, language: Language): PatternStops =
        call("pattern-stops ${id.value} ${pattern.value} $language") {
            patternStopsOverride?.invoke(id, pattern) ?: FixtureDomain.patternStops(id, pattern)
        }

    override suspend fun polylines(id: RouteId, patterns: List<PatternSuffix>): List<RoutePolyline> =
        call("polylines ${id.value} ${patterns.joinToString(",") { it.value }}") {
            polylinesOverride?.invoke(id, patterns)
                ?: FixtureDomain.polylines(id).filter { it.pattern in patterns }
        }

    override suspend fun positions(id: RouteId, patterns: List<PatternSuffix>): RoutePositions =
        error("not used by the data layer")

    override suspend fun plan(request: TripRequest, language: Language): TripPlan = error("not used by the data layer")

    override suspend fun geocode(query: String, language: Language, bounds: BoundingBox): List<GeocodeResult> =
        error("not used by the data layer")

    override suspend fun reverseGeocode(at: LatLon, language: Language): List<GeocodeResult> =
        error("not used by the data layer")
}
