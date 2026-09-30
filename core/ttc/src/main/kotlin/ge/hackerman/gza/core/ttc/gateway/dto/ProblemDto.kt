package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.Serializable

/** RFC 7807 error body (`application/problem+json`). */
@Serializable
internal data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null
)
