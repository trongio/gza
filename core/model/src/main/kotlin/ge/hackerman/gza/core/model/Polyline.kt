package ge.hackerman.gza.core.model

/**
 * A Google encoded polyline at precision 1e5, as the gateway sends it. Kept encoded because
 * it is compact to store; [decode] is pure and cheap.
 */
@JvmInline
value class EncodedPolyline(val value: String) {
    /** Malformed input stops at the last whole point instead of throwing. */
    fun decode(): List<LatLon> =
        generateSequence(nextPoint(Point(0, 0L, 0L, null))) { nextPoint(it) }.mapNotNull { it.location }.toList()

    private class Point(val end: Int, val lat: Long, val lon: Long, val location: LatLon?)

    // Null at the end of the input, or at the first chunk that does not make a whole valid point.
    private fun nextPoint(previous: Point): Point? {
        val dLat = readValue(previous.end)
        val dLon = dLat?.let { readValue(it.end) }
        if (dLat == null || dLon == null) return null
        val lat = previous.lat + dLat.delta
        val lon = previous.lon + dLon.delta
        return LatLon.ofOrNull(lat / PRECISION, lon / PRECISION)?.let { Point(dLon.end, lat, lon, it) }
    }

    private class Value(val delta: Long, val end: Int)

    private fun readValue(start: Int): Value? {
        var index = start
        var result = 0L
        var shift = 0
        val limit = minOf(value.length, start + MAX_CHUNKS)
        var decoded: Value? = null
        var chunk = CONTINUATION
        while (decoded == null && chunk in CONTINUATION..MAX_CHUNK && index < limit) {
            chunk = value[index++].code - ASCII_OFFSET
            result = result or ((chunk and LOW_BITS).toLong() shl shift)
            shift += BITS_PER_CHUNK
            if (chunk in 0 until CONTINUATION) {
                decoded = Value(if (result and 1L != 0L) (result shr 1).inv() else result shr 1, index)
            }
        }
        return decoded
    }

    private companion object {
        const val PRECISION = 1e5
        const val ASCII_OFFSET = 63
        const val CONTINUATION = 0x20
        const val LOW_BITS = 0x1F
        const val MAX_CHUNK = 0x3F
        const val BITS_PER_CHUNK = 5

        // More than 7 chunks cannot come from a real coordinate; stop instead of overflowing.
        const val MAX_CHUNKS = 7
    }
}

/** The drawn shape of one pattern. */
data class RoutePolyline(val pattern: PatternSuffix, val encoded: EncodedPolyline, val color: RouteColor?)
