package ge.hackerman.gza.core.data.database

import ge.hackerman.gza.core.model.ServiceMinute

/**
 * Service minutes as big-endian unsigned 16-bit values, 2 bytes each, in the order given.
 * A service minute is 0..2879 (hours 0..47), relative to its service day: `24:05` stays 1445,
 * never an instant, so the stored timetable does not depend on any time zone.
 */
internal object PackedMinutes {
    fun pack(times: List<ServiceMinute>): ByteArray {
        val bytes = ByteArray(times.size * BYTES_PER_VALUE)
        times.forEachIndexed { index, time ->
            bytes[index * BYTES_PER_VALUE] = (time.minutes shr BITS_PER_BYTE).toByte()
            bytes[index * BYTES_PER_VALUE + 1] = time.minutes.toByte()
        }
        return bytes
    }

    /** Skips values that are not a service minute and a trailing odd byte, never throws. */
    fun unpack(bytes: ByteArray): List<ServiceMinute> {
        val count = bytes.size / BYTES_PER_VALUE
        val times = ArrayList<ServiceMinute>(count)
        for (index in 0 until count) {
            val high = bytes[index * BYTES_PER_VALUE].toInt() and BYTE_MASK
            val low = bytes[index * BYTES_PER_VALUE + 1].toInt() and BYTE_MASK
            val value = (high shl BITS_PER_BYTE) or low
            if (value < MAX_SERVICE_MINUTE_EXCLUSIVE) times += ServiceMinute(value)
        }
        return times
    }

    private const val BYTES_PER_VALUE = 2
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF

    // ServiceMinute's own bound: 48 hours.
    private const val MAX_SERVICE_MINUTE_EXCLUSIVE = 48 * 60
}
