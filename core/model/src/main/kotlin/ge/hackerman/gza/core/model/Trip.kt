package ge.hackerman.gza.core.model

import java.time.Duration
import java.time.Instant

/** Every kind a trip may ride; [TransportKind.UNKNOWN] is not something to ask for. */
val TRANSIT_KINDS: Set<TransportKind> =
    setOf(TransportKind.BUS, TransportKind.MINIBUS, TransportKind.METRO, TransportKind.CABLE_CAR)

/** A journey planner query. Walking is always allowed. */
data class TripRequest(
    val from: LatLon,
    val to: LatLon,
    val time: TripTime = TripTime.LeaveNow,
    val kinds: Set<TransportKind> = TRANSIT_KINDS,
    val optimize: TripOptimize = TripOptimize.QUICK
)

/**
 * When to travel. An [Instant], not a local time, so a caller cannot pass a device-zone
 * wall clock by accident; the client formats it in Tbilisi time.
 */
sealed interface TripTime {
    data object LeaveNow : TripTime

    data class DepartAt(val at: Instant) : TripTime

    data class ArriveBy(val at: Instant) : TripTime
}

enum class TripOptimize { QUICK, LESS_WALKING }

data class TripPlan(val from: Place, val to: Place, val itineraries: List<Itinerary>)

/** A point in a plan. The gateway gives no stop id here, only a name and coordinates. */
data class Place(val name: String?, val location: LatLon)

data class Itinerary(
    val start: Instant,
    val end: Instant,
    val duration: Duration,
    val walkTime: Duration,
    val walkDistanceMeters: Double,
    val legs: List<Leg>
)

/** One part of an itinerary: a walk or a ride. */
sealed interface Leg {
    val from: Place
    val to: Place
    val start: Instant
    val end: Instant
    val distanceMeters: Double?
    val duration: Duration?
    val polyline: EncodedPolyline?
}

data class WalkLeg(
    override val from: Place,
    override val to: Place,
    override val start: Instant,
    override val end: Instant,
    override val distanceMeters: Double?,
    override val duration: Duration?,
    override val polyline: EncodedPolyline?,
    val steps: List<WalkStep>
) : Leg

data class TransitLeg(
    override val from: Place,
    override val to: Place,
    override val start: Instant,
    override val end: Instant,
    override val distanceMeters: Double?,
    override val duration: Duration?,
    override val polyline: EncodedPolyline?,
    /** Minibus legs say BUS: the plan's route has no id to tell them apart. */
    val kind: TransportKind,
    val route: LegRoute?,
    val realtime: Boolean,
    /**
     * Raw `arrivalDelay`. Seen as 3676, 4607 and -3631 seconds on buses that were roughly on
     * time, so it is a hint only (PLAN.md 1.2), never applied to [end].
     */
    val arrivalDelayHint: Duration?,
    val intermediateStops: List<Stop>
) : Leg

/** The route a plan leg rides. [id] is null in every plan response seen so far. */
data class LegRoute(val id: RouteId?, val shortName: String?, val longName: String?, val color: RouteColor?)

data class WalkStep(
    val relativeDirection: String?,
    val streetName: String?,
    val distanceMeters: Double?,
    val location: LatLon?
)
