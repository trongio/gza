package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.predict.testing.PredictFixtures
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_301
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_326
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_472
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_551
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ServiceCalendarTest {
    private val p326 = PredictFixtures.schedule("schedule/326-0-01-en.json", ROUTE_326, "0:01").periods
    private val p301 = PredictFixtures.schedule("schedule/301-0-01-en.json", ROUTE_301, "0:01").periods
    private val p551 = PredictFixtures.schedule("schedule/551-0-01-en.json", ROUTE_551, "0:01").periods
    private val p472 = PredictFixtures.schedule("schedule/472-0-03-en.json", ROUTE_472, "0:03").periods

    private fun period(from: DayOfWeek, to: DayOfWeek, vararg dates: String) =
        ServicePeriod(from, to, dates.map(LocalDate::parse).toSet(), emptyList())

    private fun date(iso: String) = LocalDate.parse(iso)

    @Test
    fun `listed date wins over the weekday range`() {
        val weekday = period(MONDAY, FRIDAY, "2026-09-28", "2026-10-03")
        val weekend = period(SATURDAY, SUNDAY, "2026-10-04")
        assertSame(weekday, listOf(weekday, weekend).periodFor(date("2026-10-03")))
    }

    @Test
    fun `a date inside the listed week that no period lists has no service`() {
        val periods = p551.map {
            if (it.fromDay == MONDAY) it.copy(serviceDates = it.serviceDates - date("2026-09-30")) else it
        }
        assertNull(periods.periodFor(date("2026-09-30")))
        assertSame(periods[0], periods.periodFor(date("2026-10-01")))
    }

    @Test
    fun `past the listed week the weekday decides`() {
        assertSame(p326[0], p326.periodFor(date("2026-10-05")))
        assertSame(p326[1], p326.periodFor(date("2026-10-11")))
        assertEquals(SATURDAY, p301.periodFor(date("2026-10-10"))!!.fromDay)
        assertEquals(SUNDAY, p301.periodFor(date("2026-10-11"))!!.fromDay)
        assertEquals(MONDAY to SATURDAY, p472.periodFor(date("2026-10-10"))!!.let { it.fromDay to it.toDay })
    }

    @Test
    fun `before the listed week the weekday decides too`() {
        assertSame(p326[0], p326.periodFor(date("2026-09-21")))
        assertSame(p326[1], p326.periodFor(date("2026-09-27")))
    }

    @Test
    fun `wrap around range covers friday to monday only`() {
        val wrap = period(FRIDAY, MONDAY)
        listOf(FRIDAY, SATURDAY, SUNDAY, MONDAY).forEach { assertTrue(wrap.coversWeekday(it), "$it") }
        listOf(TUESDAY, WEDNESDAY, THURSDAY).forEach { assertFalse(wrap.coversWeekday(it), "$it") }
        assertEquals(4, wrap.weekdayCount())
        assertEquals(1, period(SUNDAY, SUNDAY).weekdayCount())
        assertEquals(7, period(MONDAY, SUNDAY).weekdayCount())
        assertEquals(7, period(TUESDAY, MONDAY).weekdayCount())
        assertSame(wrap, listOf(wrap).periodFor(date("2026-10-11")))
        assertNull(listOf(wrap).periodFor(date("2026-10-14")))
    }

    @Test
    fun `overlapping ranges on a saturday past the window take the single day`() {
        val long = period(MONDAY, SATURDAY, "2026-09-28")
        val single = period(SATURDAY, SATURDAY, "2026-09-29")
        assertSame(single, listOf(long, single).periodFor(date("2026-10-10")))
        assertSame(single, listOf(single, long).periodFor(date("2026-10-10")))
    }

    @Test
    fun `equal ranges keep list order`() {
        val first = period(SATURDAY, SUNDAY)
        val second = period(SATURDAY, SUNDAY)
        assertSame(first, listOf(first, second).periodFor(date("2026-10-10")))
    }

    @Test
    fun `only weekday periods on a saturday is an empty service day`() {
        assertNull(listOf(period(MONDAY, FRIDAY, "2026-09-28")).periodFor(date("2026-10-10")))
    }

    @Test
    fun `no periods means no service`() {
        assertNull(emptyList<ServicePeriod>().periodFor(date("2026-09-28")))
    }

    @Test
    fun `periods without service dates match by weekday alone`() {
        val weekday = period(MONDAY, FRIDAY)
        val weekend = period(SATURDAY, SUNDAY)
        assertSame(weekday, listOf(weekday, weekend).periodFor(date("2026-09-30")))
        assertSame(weekend, listOf(weekday, weekend).periodFor(date("2026-10-04")))
    }

    @Test
    fun `every day of 2026 and 2027 follows the rule for 326`() {
        val listed = p326.flatMap { period -> period.serviceDates.map { it to period } }.toMap()
        var date = date("2026-01-01")
        while (date.year < 2028) {
            val expected = listed[date] ?: if (date.dayOfWeek <= FRIDAY) p326[0] else p326[1]
            assertSame(expected, p326.periodFor(date), "$date")
            date = date.plusDays(1)
        }
    }
}
