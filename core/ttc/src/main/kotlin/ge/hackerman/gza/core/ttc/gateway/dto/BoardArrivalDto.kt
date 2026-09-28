package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class BoardArrivalDto(
    val shortName: String? = null,
    val color: String? = null,
    val headsign: String? = null,
    val patternSuffix: String? = null,
    val vehicleMode: String? = null,
    val realtime: Boolean? = null,
    val realtimeArrivalMinutes: Int? = null,
    val scheduledArrivalMinutes: Int? = null
)
