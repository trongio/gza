package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class ServicePeriodDto(
    val fromDay: String? = null,
    val toDay: String? = null,
    @Serializable(with = LenientStrings::class) val serviceDates: List<String?>? = null,
    @Serializable(with = LenientScheduledStops::class) val stops: List<ScheduledStopDto?>? = null
)

@Serializable
internal data class ScheduledStopDto(
    val id: String? = null,
    val name: String? = null,
    val position: Int? = null,
    /** Comma-separated `H:mm`, can go past `24:00`. */
    val arrivalTimes: String? = null
)
