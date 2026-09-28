package ge.hackerman.gza.core.model

import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ServiceMinuteTest {
    @Test
    fun `hours and minutes parse into minutes after midnight`() {
        assertEquals(475, ServiceMinute.parseOrNull("7:55")?.minutes)
        assertEquals(425, ServiceMinute.parseOrNull("07:05")?.minutes)
        assertEquals(0, ServiceMinute.parseOrNull("0:00")?.minutes)
        assertEquals(47 * 60 + 59, ServiceMinute.parseOrNull("47:59")?.minutes)
    }

    @Test
    fun `times past midnight stay on their service day`() {
        val late = requireNotNull(ServiceMinute.parseOrNull("24:05"))
        assertEquals(1445, late.minutes)
        assertEquals(
            ZonedDateTime.parse("2026-09-29T00:05+04:00[Asia/Tbilisi]"),
            late.atDate(LocalDate.of(2026, 9, 28))
        )
    }

    @Test
    fun `order is kept across midnight`() {
        val before = requireNotNull(ServiceMinute.parseOrNull("23:59"))
        val after = requireNotNull(ServiceMinute.parseOrNull("24:10"))
        assertTrue(before.minutes < after.minutes)
        val date = LocalDate.of(2026, 9, 28)
        assertTrue(before.atDate(date).isBefore(after.atDate(date)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["48:00", "7:60", "7", "7:5a", "-1:00", "", " ", "7:5", "123:00"])
    fun `malformed times are null`(value: String) {
        assertNull(ServiceMinute.parseOrNull(value))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(475, ServiceMinute.parseOrNull(" 7:55 ")?.minutes)
    }
}
