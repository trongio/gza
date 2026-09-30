package ge.hackerman.gza.core.ttc.gateway.mapper

import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** ISO with an offset (`.000+00:00` or `Z`), or OpenTripPlanner's epoch milliseconds. */
internal fun JsonElement?.toInstantOrNull(): Instant? {
    val primitive = this as? JsonPrimitive
    return when {
        primitive == null -> null
        primitive.isString -> parseIsoOrNull(primitive.content)
        else -> primitive.longOrNull?.let(::epochMillisOrNull)
    }
}

private fun parseIsoOrNull(raw: String): Instant? = try {
    OffsetDateTime.parse(raw.trim()).toInstant()
} catch (_: DateTimeParseException) {
    null
}

private fun epochMillisOrNull(millis: Long): Instant? = try {
    Instant.ofEpochMilli(millis)
} catch (_: DateTimeException) {
    null
}
