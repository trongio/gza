package ge.hackerman.gza.core.ttc.gateway

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
import ge.hackerman.gza.core.model.TBILISI_BOUNDS
import ge.hackerman.gza.core.model.TripPlan
import ge.hackerman.gza.core.model.TripRequest

/**
 * Typed access to every TTC gateway endpoint Gza uses. Main-safe: the network runs on
 * OkHttp's threads and decoding and mapping on the dispatcher given to
 * [TtcGatewayClientFactory.create]. Every failure is a [TtcGatewayException].
 *
 * Lists are lenient item by item: a mistyped or incomplete item is dropped and the rest are
 * returned. An empty result means the gateway really sent nothing (`[]`, `null` or only
 * nulls). When it sent items and not one of them is usable, whether because none decodes or
 * because each decodes without what the item needs (every field renamed, say), the response
 * is [TtcGatewayException.Malformed], never an empty list, so a caller can safely treat empty
 * as empty. The same holds for the vehicles of any one pattern in [positions] and for the
 * entries of [polylines]. Nested lists follow the rule by dropping their parent: a period of
 * [schedule] whose dates or stops, or an itinerary of [plan] whose steps or intermediate
 * stops, were sent but are all unusable is dropped, and if that leaves none the response is
 * malformed. A [route] whose patterns were sent but are all unusable is malformed.
 */
@Suppress("TooManyFunctions") // Mirrors the gateway, one function per endpoint.
interface TtcGatewayClient {
    suspend fun stops(language: Language): List<Stop>

    /** An unknown id is `Http(500)`: the gateway does not 404. */
    suspend fun stop(id: StopId, language: Language): Stop

    suspend fun stopRoutes(id: StopId, language: Language): List<Route>

    /** The board's minutes are hints (0 at a terminus for a parked bus); see [StopBoard]. */
    suspend fun arrivalBoard(id: StopId, language: Language): StopBoard

    /** Buses, minibuses, metro and cable cars. */
    suspend fun routes(language: Language): List<Route>

    suspend fun route(id: RouteId, language: Language): RouteDetail

    suspend fun schedule(id: RouteId, pattern: PatternSuffix, language: Language): RouteSchedule

    /**
     * One pattern per call on purpose: asking for several returns one merged list that is not
     * in travel order.
     */
    suspend fun patternStops(id: RouteId, pattern: PatternSuffix, language: Language): PatternStops

    /** @throws IllegalArgumentException for an empty [patterns] list, before any request. */
    suspend fun polylines(id: RouteId, patterns: List<PatternSuffix>): List<RoutePolyline>

    /** @throws IllegalArgumentException for an empty [patterns] list, before any request. */
    suspend fun positions(id: RouteId, patterns: List<PatternSuffix>): RoutePositions

    suspend fun plan(request: TripRequest, language: Language): TripPlan

    suspend fun geocode(query: String, language: Language, bounds: BoundingBox = TBILISI_BOUNDS): List<GeocodeResult>

    suspend fun reverseGeocode(at: LatLon, language: Language): List<GeocodeResult>
}
