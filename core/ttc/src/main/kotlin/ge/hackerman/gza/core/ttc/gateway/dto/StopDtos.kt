package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable

// Every property is nullable with a null default, even ones never seen null: a new null
// anywhere must drop one item in the mapper, never fail a whole response.

@Serializable
internal data class StopDto(
    val id: String? = null,
    val code: String? = null,
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val vehicleMode: String? = null
)

@Serializable
internal data class StopRefDto(val id: String? = null, val name: String? = null)

/** One entry of `stops-of-patterns`: a stop and the requested patterns that serve it. */
@Serializable
internal data class PatternStopDto(
    val stop: StopDto? = null,
    @Serializable(with = LenientStrings::class) val patternSuffixes: List<String?>? = null
)
