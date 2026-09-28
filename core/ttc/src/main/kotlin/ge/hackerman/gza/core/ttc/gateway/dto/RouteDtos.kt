package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class RouteDto(
    val id: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val color: String? = null,
    val mode: String? = null
)

@Serializable
internal data class RouteDetailDto(
    val id: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val color: String? = null,
    val mode: String? = null,
    val patterns: List<PatternDto?>? = null,
    val defaultPatternSuffix: String? = null
)

@Serializable
internal data class PatternDto(
    val patternSuffix: String? = null,
    val directionId: Int? = null,
    val firstStop: StopRefDto? = null,
    val lastStop: StopRefDto? = null,
    val headsign: String? = null
)
