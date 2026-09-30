package ge.hackerman.gza.core.data.database

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class ConvertersTest {
    @Test
    fun `instants are epoch millis`() {
        val instant = Instant.parse("2026-09-28T13:11:00.123Z")
        assertEquals(instant.toEpochMilli(), Converters.instantToMillis(instant))
        assertEquals(instant, Converters.millisToInstant(Converters.instantToMillis(instant)))
        assertNull(Converters.instantToMillis(null))
        assertNull(Converters.millisToInstant(null))
    }

    @Test
    fun `days are iso 1 to 7 and anything else is null`() {
        DayOfWeek.entries.forEach { day ->
            assertEquals(day.value, Converters.dayOfWeekToInt(day))
            assertEquals(day, Converters.intToDayOfWeek(day.value))
        }
        listOf(0, 8, -1, Int.MAX_VALUE).forEach { assertNull(Converters.intToDayOfWeek(it)) }
    }

    @Test
    fun `service dates round trip, empty included, and bad entries are skipped`() {
        val dates = listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29))
        assertEquals("2026-09-28,2026-09-29", Converters.datesToText(dates))
        assertEquals(dates, Converters.textToDates(Converters.datesToText(dates)))
        assertEquals("", Converters.datesToText(emptyList()))
        assertEquals(emptyList(), Converters.textToDates(""))
        assertEquals(dates, Converters.textToDates("2026-09-28,not-a-date,,2026-09-29"))
    }
}
