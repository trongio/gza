package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.TBILISI_ZONE
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TbilisiClockTest {
    private fun utcClock(instant: String) = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)

    @Test
    fun `utc afternoon is four hours later in Tbilisi`() {
        val now = utcClock("2026-09-28T13:11:00Z").nowInTbilisi()
        assertEquals(LocalDate.of(2026, 9, 28), now.toLocalDate())
        assertEquals(LocalTime.of(17, 11), now.toLocalTime())
    }

    @Test
    fun `local date rolls over past midnight while utc has not`() {
        val now = utcClock("2026-09-28T20:30:00Z").nowInTbilisi()
        assertEquals(LocalDate.of(2026, 9, 29), now.toLocalDate())
        assertEquals(LocalTime.of(0, 30), now.toLocalTime())
    }

    @Test
    fun `sunday evening utc is already monday in Tbilisi`() {
        val clock = utcClock("2026-10-04T20:30:00Z")
        assertEquals(DayOfWeek.SUNDAY, Instant.now(clock).atZone(ZoneOffset.UTC).dayOfWeek)
        val now = clock.nowInTbilisi()
        assertEquals(LocalDate.of(2026, 10, 5), now.toLocalDate())
        assertEquals(DayOfWeek.MONDAY, now.dayOfWeek)
    }

    @Test
    fun `result zone is Tbilisi regardless of the clock zone`() {
        assertEquals(TBILISI_ZONE, utcClock("2026-09-28T13:11:00Z").nowInTbilisi().zone)
    }
}
