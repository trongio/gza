package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.EncodedPolyline
import ge.hackerman.gza.core.model.Itinerary
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.Leg
import ge.hackerman.gza.core.model.LegRoute
import ge.hackerman.gza.core.model.Place
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.TransitLeg
import ge.hackerman.gza.core.model.TripPlan
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.WalkLeg
import ge.hackerman.gza.core.model.WalkStep
import ge.hackerman.gza.core.ttc.gateway.dto.ItineraryDto
import ge.hackerman.gza.core.ttc.gateway.dto.LegDto
import ge.hackerman.gza.core.ttc.gateway.dto.LegRouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.PlaceDto
import ge.hackerman.gza.core.ttc.gateway.dto.PlanResponseDto
import ge.hackerman.gza.core.ttc.gateway.dto.PolylineDto
import ge.hackerman.gza.core.ttc.gateway.dto.StepDto
import java.time.Duration
import java.time.Instant

/** The plan's own endpoints fall back to the request's when the response lacks them. */
internal fun PlanResponseDto.toTripPlan(request: TripRequest): TripPlan = TripPlan(
    from = from?.toPlaceOrNull() ?: Place(null, request.from),
    to = to?.toPlaceOrNull() ?: Place(null, request.to),
    itineraries = itineraries.orEmpty().mapNotNull { it?.toItineraryOrNull() }
)

/**
 * Null when a time is unreadable, the itinerary ends before it starts, it has no legs, or any
 * leg is invalid: a plan with a hole is worse than none, and an empty or backwards one is
 * nothing a rider can follow.
 */
internal fun ItineraryDto.toItineraryOrNull(): Itinerary? {
    val times = orderedOrNull(startTime.toInstantOrNull(), endTime.toInstantOrNull())
    val mappedLegs = legs.orEmpty().map { it?.toLegOrNull() }
    return if (times == null || mappedLegs.isEmpty() || null in mappedLegs) {
        null
    } else {
        val (start, end) = times
        Itinerary(
            start = start,
            end = end,
            duration = duration?.let(Duration::ofSeconds) ?: Duration.between(start, end),
            walkTime = walkTime?.let(Duration::ofSeconds) ?: Duration.ZERO,
            walkDistanceMeters = walkDistance?.takeIf { it.isFinite() } ?: 0.0,
            legs = mappedLegs.filterNotNull()
        )
    }
}

/** Null without both places and both times, or when the leg ends before it starts. */
internal fun LegDto.toLegOrNull(): Leg? {
    val times = orderedOrNull(startTime.toInstantOrNull(), endTime.toInstantOrNull())
    val places = bothOrNull(from?.toPlaceOrNull(), to?.toPlaceOrNull())
    return if (times == null || places == null) {
        null
    } else if (mode?.trim().equals(WALK, ignoreCase = true)) {
        WalkLeg(
            from = places.first,
            to = places.second,
            start = times.first,
            end = times.second,
            distanceMeters = distance?.takeIf { it.isFinite() },
            duration = duration?.let(Duration::ofSeconds),
            polyline = legPolyline.toEncodedOrNull(),
            steps = steps.orEmpty().mapNotNull { it?.toWalkStep() }
        )
    } else {
        val legRoute = route?.toLegRoute()
        TransitLeg(
            from = places.first,
            to = places.second,
            start = times.first,
            end = times.second,
            distanceMeters = distance?.takeIf { it.isFinite() },
            duration = duration?.let(Duration::ofSeconds),
            polyline = legPolyline.toEncodedOrNull(),
            kind = KindMapping.fromMode(mode, legRoute?.id),
            route = legRoute,
            realtime = realTime ?: false,
            // Raw, never clamped: 3676 or -3631 on an on-time bus is exactly what T10 must distrust.
            arrivalDelayHint = arrivalDelay?.let(Duration::ofSeconds),
            intermediateStops = intermediateStops.toStops()
        )
    }
}

/** Both instants, and only when the end is not before the start. */
private fun orderedOrNull(start: Instant?, end: Instant?): Pair<Instant, Instant>? =
    bothOrNull(start, end)?.takeUnless { (from, to) -> to < from }

private fun <A : Any, B : Any> bothOrNull(a: A?, b: B?): Pair<A, B>? = if (a != null && b != null) a to b else null

internal fun PlaceDto.toPlaceOrNull(): Place? =
    LatLon.ofOrNull(lat, lon)?.let { Place(name?.takeIf { name -> name.isNotBlank() }, it) }

private fun LegRouteDto.toLegRoute(): LegRoute = LegRoute(
    id = RouteId.ofOrNull(id),
    shortName = shortName?.takeIf { it.isNotBlank() },
    longName = longName?.takeIf { it.isNotBlank() },
    color = RouteColor.ofHexOrNull(color)
)

private fun StepDto.toWalkStep(): WalkStep = WalkStep(
    relativeDirection = relativeDirection?.takeIf { it.isNotBlank() },
    streetName = streetName?.takeIf { it.isNotBlank() },
    distanceMeters = distance?.takeIf { it.isFinite() },
    location = LatLon.ofOrNull(lat, lon)
)

private fun PolylineDto?.toEncodedOrNull(): EncodedPolyline? =
    this?.encodedValue?.takeIf { it.isNotBlank() }?.let(::EncodedPolyline)

private const val WALK = "WALK"
