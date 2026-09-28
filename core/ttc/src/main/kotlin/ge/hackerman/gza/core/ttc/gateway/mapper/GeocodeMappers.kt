package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.BoundingBox
import ge.hackerman.gza.core.model.GeocodeResult
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.ttc.gateway.dto.FeatureCollectionDto
import ge.hackerman.gza.core.ttc.gateway.dto.FeatureDto
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed

internal fun FeatureCollectionDto.toGeocodeResults(): List<GeocodeResult> =
    features.mapEachOrMalformed { it.toGeocodeResultOrNull() }

/** GeoJSON order is `[lon, lat]`. Only points are places; anything else is dropped. */
internal fun FeatureDto.toGeocodeResultOrNull(): GeocodeResult? {
    val isPoint = geometry?.type == null || geometry.type.equals(POINT, ignoreCase = true)
    val coordinates = geometry?.coordinates.orEmpty()
    val location = if (isPoint) LatLon.ofOrNull(coordinates.getOrNull(1), coordinates.getOrNull(0)) else null
    return location?.let {
        GeocodeResult(
            name = properties?.name?.takeIf { name -> name.isNotBlank() },
            location = it,
            street = properties?.street,
            houseNumber = properties?.houseNumber,
            locality = properties?.locality,
            district = properties?.district,
            city = properties?.city,
            postcode = properties?.postcode,
            osmId = properties?.osmId,
            osmKey = properties?.osmKey,
            osmValue = properties?.osmValue,
            type = properties?.type,
            extent = properties?.extent?.toBoundingBoxOrNull()
        )
    }
}

/** Photon's `[minLon, maxLat, maxLon, minLat]`, normalised with min/max whatever the order. */
private fun List<Double?>.toBoundingBoxOrNull(): BoundingBox? {
    val values = filterNotNull().filter { it.isFinite() }
    return if (size != EXTENT_SIZE || values.size != EXTENT_SIZE) {
        null
    } else {
        // Two corners, each [lon, lat].
        val (a, b) = values.chunked(2)
        BoundingBox(
            minLon = minOf(a[0], b[0]),
            minLat = minOf(a[1], b[1]),
            maxLon = maxOf(a[0], b[0]),
            maxLat = maxOf(a[1], b[1])
        )
    }
}

private const val POINT = "Point"
private const val EXTENT_SIZE = 4
