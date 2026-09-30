package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.EncodedPolyline
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.ttc.gateway.dto.PolylineDto
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed

/**
 * An entry under an invalid key or without a line is dropped; a null entry is nothing sent.
 * When entries were sent and not one is usable, the response is malformed.
 */
internal fun Map<String, PolylineDto?>?.toRoutePolylines(): List<RoutePolyline> =
    orEmpty().map { (key, dto) -> dto?.let { key to it } }.mapEachOrMalformed { (key, dto) ->
        val pattern = PatternSuffix.ofOrNull(key)
        val encoded = dto.encodedValue?.takeIf { it.isNotBlank() }
        if (pattern == null || encoded == null) {
            null
        } else {
            RoutePolyline(pattern, EncodedPolyline(encoded), RouteColor.ofHexOrNull(dto.color))
        }
    }
