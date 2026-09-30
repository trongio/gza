package ge.hackerman.gza.core.model

import java.time.Instant

/** Live vehicles of one route, as seen at [fetchedAt]. */
data class RoutePositions(val routeId: RouteId, val fetchedAt: Instant, val vehicles: List<VehiclePosition>)

/**
 * One vehicle's GPS report. [headingDegrees] and [nextStopId] are independent and often
 * null: both null at a terminus is the layover signal `:core:predict` reads, so they are
 * kept exactly as sent.
 */
data class VehiclePosition(
    val vehicleId: VehicleId,
    val pattern: PatternSuffix,
    val location: LatLon,
    val headingDegrees: Double?,
    val nextStopId: StopId?
)
