package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import kotlin.math.cos
import kotlin.math.sqrt

/** Metres to [other]. Equirectangular: well under 1 % off at city scale, and cheap. */
fun LatLon.metersTo(other: LatLon): Double {
    val dLat = (other.lat - lat) * METERS_PER_DEGREE
    val dLon = (other.lon - lon) * METERS_PER_DEGREE * cos(Math.toRadians((lat + other.lat) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

private const val METERS_PER_DEGREE = 111_320.0
