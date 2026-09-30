package ge.hackerman.gza.core.data.database

import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.ServiceMinute
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PackedMinutesTest {
    private fun minutes(vararg values: Int) = values.map(::ServiceMinute)

    @Test
    fun `empty packs to nothing`() {
        assertEquals(0, PackedMinutes.pack(emptyList()).size)
        assertEquals(emptyList(), PackedMinutes.unpack(ByteArray(0)))
    }

    @Test
    fun `boundaries and times past midnight round trip`() {
        val times = minutes(0, 1, 1439, 1440, 1445, 2879)
        val packed = PackedMinutes.pack(times)
        assertEquals(12, packed.size)
        assertEquals(times, PackedMinutes.unpack(packed))
    }

    @Test
    fun `values are big endian two bytes each`() {
        assertContentEquals(byteArrayOf(0x05, 0xA5.toByte()), PackedMinutes.pack(minutes(1445)))
    }

    @Test
    fun `order is kept exactly, even descending`() {
        val times = minutes(900, 30, 1500, 30)
        assertEquals(times, PackedMinutes.unpack(PackedMinutes.pack(times)))
    }

    @Test
    fun `a trailing odd byte is ignored`() {
        val packed = PackedMinutes.pack(minutes(427, 441)) + byteArrayOf(0x01)
        assertEquals(minutes(427, 441), PackedMinutes.unpack(packed))
    }

    @Test
    fun `values that are not service minutes are skipped, not thrown`() {
        val bytes = byteArrayOf(0x01, 0xAB.toByte(), 0x0B, 0x40, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x05)
        // 0x01AB = 427, 0x0B40 = 2880, 0xFFFF = 65535, 0x0005 = 5
        assertEquals(minutes(427, 5), PackedMinutes.unpack(bytes))
    }

    @Test
    fun `every stop row of the busiest recorded schedule round trips`() {
        // 301 0:01 has 75 stop rows with times past midnight.
        val schedule = FixtureDomain.schedule(FixtureDomain.routeId("301"), PatternSuffix("0:01"))
        val rows = schedule.periods.flatMap { it.stops }
        assertTrue(rows.count { row -> row.times.any { it.minutes >= 1440 } } >= 75)
        rows.forEach { assertEquals(it.times, PackedMinutes.unpack(PackedMinutes.pack(it.times))) }
    }
}
