package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class VehiclePositionDto(
    val vehicleId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val heading: Double? = null,
    val nextStopId: String? = null
)
