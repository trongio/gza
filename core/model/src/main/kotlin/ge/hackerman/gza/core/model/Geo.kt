package ge.hackerman.gza.core.model

/** A WGS84 point. Always latitude first here, whatever order the gateway used. */
data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(isValid(lat, lon)) { "invalid coordinates: $lat, $lon" }
    }

    companion object {
        /** Null for missing, non-finite or out-of-range coordinates, never an exception. */
        fun ofOrNull(lat: Double?, lon: Double?): LatLon? =
            if (lat != null && lon != null && isValid(lat, lon)) LatLon(lat, lon) else null

        private fun isValid(lat: Double, lon: Double): Boolean =
            lat.isFinite() && lon.isFinite() && lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON
    }
}

/** An axis-aligned box. Field order matches the gateway's `bbox` query: min lon, min lat, max lon, max lat. */
data class BoundingBox(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    init {
        require(minLon <= maxLon && minLat <= maxLat) { "inverted bounding box" }
    }

    operator fun contains(point: LatLon): Boolean = point.lat in minLat..maxLat && point.lon in minLon..maxLon
}

private const val MIN_LAT = -90.0
private const val MAX_LAT = 90.0
private const val MIN_LON = -180.0
private const val MAX_LON = 180.0
