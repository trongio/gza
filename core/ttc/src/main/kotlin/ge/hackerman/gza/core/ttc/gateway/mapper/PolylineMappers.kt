package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.EncodedPolyline
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.ttc.gateway.dto.PolylineDto

internal fun Map<String, PolylineDto?>?.toRoutePolylines(): List<RoutePolyline> = orEmpty().mapNotNull { (key, dto) ->
    val pattern = PatternSuffix.ofOrNull(key)
    val encoded = dto?.encodedValue?.takeIf { it.isNotBlank() }
    if (pattern == null || encoded == null) {
        null
    } else {
        RoutePolyline(pattern, EncodedPolyline(encoded), RouteColor.ofHexOrNull(dto.color))
    }
}
