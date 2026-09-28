package ge.hackerman.gza.core.ttc.gateway.dto

import ge.hackerman.gza.core.ttc.TtcJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Positions and polylines are objects keyed by pattern suffix, so their keys are data, not
 * fields: an extra key of another shape (say `"timestamp": 123`) must drop that entry, not
 * the response. Each value is decoded on its own; one that does not fit becomes null.
 */
internal inline fun <reified T> Map<String, JsonElement>.decodeEachValue(json: Json = TtcJson): Map<String, T?> =
    mapValues { (_, value) ->
        try {
            json.decodeFromJsonElement<T>(value)
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }
    }

internal fun Map<String, JsonElement>.toPositionDtos(): Map<String, List<VehiclePositionDto?>?> =
    decodeEachValue<List<VehiclePositionDto?>?>().mapValues { it.value }

internal fun Map<String, JsonElement>.toPolylineDtos(): Map<String, PolylineDto?> = decodeEachValue<PolylineDto?>()
