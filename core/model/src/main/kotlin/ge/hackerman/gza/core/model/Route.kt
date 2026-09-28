package ge.hackerman.gza.core.model

/** A route as listed by the gateway. [longName] is Georgian for minibuses in every locale. */
data class Route(
    val id: RouteId,
    val shortName: String,
    val longName: String?,
    val color: RouteColor?,
    val kind: TransportKind
)

/** A route with its patterns. Read the pattern suffixes from here, never assume `0:01`/`1:01`. */
data class RouteDetail(
    val id: RouteId,
    val shortName: String,
    val color: RouteColor?,
    val kind: TransportKind,
    val patterns: List<Pattern>,
    val defaultPattern: PatternSuffix?
)

/** One direction of a route. */
data class Pattern(
    val suffix: PatternSuffix,
    val directionId: Int?,
    val firstStop: StopRef?,
    val lastStop: StopRef?,
    val headsign: String?
)

/** A stop mentioned by id and name only. */
data class StopRef(val id: StopId, val name: String?)

/**
 * Stops of one pattern in travel order (one request per pattern, see TTC_API.md). A loop
 * route may list a stop twice, so key these by position, never by stop id.
 */
data class PatternStops(val routeId: RouteId, val pattern: PatternSuffix, val stops: List<Stop>)
