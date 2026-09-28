package ge.hackerman.gza.core.ttc.gateway.dto

import ge.hackerman.gza.core.ttc.TtcJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

/*
 * The gateway drifts one item at a time: a stop with a numeric code, a vehicle with a string
 * heading. Decoding a whole list in one go lets that single item fail all 2,753 stops, so
 * every list is decoded element by element and a mistyped element drops only itself. A body
 * that is not the expected top-level shape still fails (SerializationException), which the
 * client reports as Malformed.
 *
 * Dropping has a limit: when a list has elements and not one of them fits, the gateway has
 * changed shape, not drifted, and returning an empty list would read as "nothing there" (a
 * sync would then wipe its cache). So an all-misfit list fails too. An empty list, or one of
 * only nulls, is what the gateway really sent (the gondola board sends `[]`) and stays empty.
 */

/** Null for a JSON null or an element that does not fit [T]. */
internal inline fun <reified T : Any> JsonElement.decodeOrNull(json: Json = TtcJson): T? =
    decodeOrNull(serializer<T>(), json)

internal fun <T : Any> JsonElement.decodeOrNull(deserializer: DeserializationStrategy<T>, json: Json): T? =
    if (this is JsonNull) {
        null
    } else if (deserializer.descriptor.kind is PrimitiveKind && this !is JsonPrimitive) {
        // kotlinx throws IndexOutOfBoundsException, not SerializationException, when a
        // primitive is decoded from an object or array at the root, so rule it out first.
        null
    } else {
        try {
            json.decodeFromJsonElement(deserializer, this)
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }
    }

/**
 * The elements of a top-level array that fit [T]. Anything but an array is malformed, and so
 * is an array with non-null elements none of which fit.
 */
internal inline fun <reified T : Any> JsonElement.decodeEachElement(json: Json = TtcJson): List<T> {
    val array = this as? JsonArray ?: throw SerializationException("Expected a JSON array, got ${shapeOf(this)}")
    val decoded = array.mapNotNull { it.decodeOrNull<T>(json) }
    requireSomeFit(array, decoded.size)
    return decoded
}

/** A single top-level object; a mistyped field fails it, since there is nothing else to keep. */
internal inline fun <reified T : Any> JsonElement.decodeObject(json: Json = TtcJson): T =
    json.decodeFromJsonElement<T>(requireObject())

internal fun JsonElement.requireObject(): JsonObject =
    this as? JsonObject ?: throw SerializationException("Expected a JSON object, got ${shapeOf(this)}")

/** Only the shape, never the content: bodies must not end up in exception messages. */
internal fun shapeOf(element: JsonElement): String = when (element) {
    is JsonObject -> "an object"
    is JsonArray -> "an array"
    JsonNull -> "null"
    else -> "a primitive"
}

/**
 * Positions and polylines are objects keyed by pattern suffix, so their keys are data, not
 * fields: an extra key of another shape (say `"timestamp": 123`) must drop that entry, not
 * the response. Within a pattern, one mistyped vehicle drops only itself, but a pattern whose
 * vehicles all misfit fails the whole response: showing no buses when the gateway sent some
 * would pass for an empty road.
 */
internal fun JsonElement.toPositionDtos(): Map<String, List<VehiclePositionDto>?> =
    requireObject().mapValues { (_, value) ->
        if (value is JsonArray) value.decodeEachElement<VehiclePositionDto>() else null
    }

internal fun JsonElement.toPolylineDtos(): Map<String, PolylineDto?> =
    requireObject().mapValues { (_, value) -> value.decodeOrNull<PolylineDto>() }

/** The plan's itineraries are decoded one by one; one mistyped itinerary drops only itself. */
internal fun JsonElement.toPlanResponseDto(): PlanResponseDto {
    val body = requireObject()
    val head = TtcJson.decodeFromJsonElement<PlanResponseDto>(JsonObject(body - ITINERARIES))
    return head.copy(itineraries = body[ITINERARIES]?.takeUnless { it is JsonNull }?.decodeEachElement<ItineraryDto>())
}

/** Same for geocoding results: one mistyped feature drops only itself. */
internal fun JsonElement.toFeatureCollectionDto(): FeatureCollectionDto {
    val body = requireObject()
    val head = TtcJson.decodeFromJsonElement<FeatureCollectionDto>(JsonObject(body - FEATURES))
    return head.copy(features = body[FEATURES]?.takeUnless { it is JsonNull }?.decodeEachElement<FeatureDto>())
}

private const val ITINERARIES = "itineraries"
private const val FEATURES = "features"
