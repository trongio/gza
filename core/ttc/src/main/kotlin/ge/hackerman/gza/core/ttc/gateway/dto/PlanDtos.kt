package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class PlanResponseDto(
    val from: PlaceDto? = null,
    val to: PlaceDto? = null,
    val itineraries: List<ItineraryDto?>? = null
)

@Serializable
internal data class PlaceDto(val lat: Double? = null, val lon: Double? = null, val name: String? = null)

/**
 * Times are ISO strings today (`2026-09-28T16:43:46.000+00:00`); OpenTripPlanner's other
 * format is epoch milliseconds, so they stay raw JSON and the mapper accepts both.
 */
@Serializable
internal data class ItineraryDto(
    val startTime: JsonElement? = null,
    val endTime: JsonElement? = null,
    val duration: Long? = null,
    val walkTime: Long? = null,
    val walkDistance: Double? = null,
    val legs: List<LegDto?>? = null
)

@Serializable
internal data class LegDto(
    val mode: String? = null,
    val from: PlaceDto? = null,
    val to: PlaceDto? = null,
    val startTime: JsonElement? = null,
    val endTime: JsonElement? = null,
    val realTime: Boolean? = null,
    val arrivalDelay: Long? = null,
    val distance: Double? = null,
    val duration: Long? = null,
    val route: LegRouteDto? = null,
    val intermediateStops: List<StopDto?>? = null,
    val legPolyline: PolylineDto? = null,
    val steps: List<StepDto?>? = null
)

/** `id` and `mode` have been null in every plan seen so far. */
@Serializable
internal data class LegRouteDto(
    val id: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val color: String? = null,
    val mode: String? = null
)

@Serializable
internal data class StepDto(
    val relativeDirection: String? = null,
    val distance: Double? = null,
    val streetName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
)
