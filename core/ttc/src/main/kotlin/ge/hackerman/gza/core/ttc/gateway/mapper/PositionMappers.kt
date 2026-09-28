package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.ttc.gateway.dto.VehiclePositionDto
import java.time.Instant

/**
 * A null heading and next stop are the layover signal, so both are kept as sent; an invalid
 * next stop id becomes null. A group under an invalid pattern key is dropped.
 */
internal fun Map<String, List<VehiclePositionDto?>?>?.toRoutePositions(
    routeId: RouteId,
    fetchedAt: Instant
): RoutePositions = RoutePositions(
    routeId = routeId,
    fetchedAt = fetchedAt,
    vehicles = orEmpty().flatMap { (key, vehicles) ->
        val pattern = PatternSuffix.ofOrNull(key)
        if (pattern == null) emptyList() else vehicles.orEmpty().mapNotNull { it?.toVehiclePositionOrNull(pattern) }
    }
)

internal fun VehiclePositionDto.toVehiclePositionOrNull(pattern: PatternSuffix): VehiclePosition? {
    val vehicle = VehicleId.ofOrNull(vehicleId)
    val location = LatLon.ofOrNull(lat, lon)
    return if (vehicle == null || location == null) {
        null
    } else {
        VehiclePosition(
            vehicleId = vehicle,
            pattern = pattern,
            location = location,
            headingDegrees = heading?.takeIf { it.isFinite() },
            nextStopId = StopId.ofOrNull(nextStopId)
        )
    }
}
