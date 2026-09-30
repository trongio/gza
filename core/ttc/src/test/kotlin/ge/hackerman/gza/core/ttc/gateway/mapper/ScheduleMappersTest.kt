package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.ttc.gateway.dto.ScheduledStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.ServicePeriodDto
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class ScheduleMappersTest {
    private val stop = ScheduledStopDto("1:970", "Ana Politkovskaia Street", 1, "7:55,8:13")
    private val period = ServicePeriodDto("SATURDAY", "SATURDAY", listOf("2026-10-03"), listOf(stop))

    @Test
    fun `a single day period maps`() {
        val mapped = requireNotNull(period.toServicePeriodOrNull())
        assertEquals(DayOfWeek.SATURDAY, mapped.fromDay)
        assertEquals(DayOfWeek.SATURDAY, mapped.toDay)
        assertEquals(setOf(LocalDate.of(2026, 10, 3)), mapped.serviceDates)
    }

    @Test
    fun `day names are trimmed and case insensitive`() {
        assertEquals(DayOfWeek.SUNDAY, period.copy(fromDay = " sunday ").toServicePeriodOrNull()?.fromDay)
    }

    @Test
    fun `an unknown day drops the period`() {
        assertNull(period.copy(fromDay = "FUNDAY").toServicePeriodOrNull())
        assertNull(period.copy(toDay = null).toServicePeriodOrNull())
    }

    @Test
    fun `bad service dates are dropped one by one`() {
        val dates = period.copy(serviceDates = listOf("2026-10-03", "03.10.2026", null, "2026-10-04"))
            .toServicePeriodOrNull()?.serviceDates
        assertEquals(setOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4)), dates)
    }

    @Test
    fun `bad time tokens are dropped and order is kept past midnight`() {
        val times = stop.copy(arrivalTimes = "7:55,,x,24:05").toScheduledStopOrNull(0)?.times
        assertEquals(listOf(475, 1445), times?.map { it.minutes })
    }

    @Test
    fun `null position uses the one based index and null times are empty`() {
        val mapped = stop.copy(position = null, arrivalTimes = null).toScheduledStopOrNull(4)
        assertEquals(5, mapped?.position)
        assertEquals(emptyList(), mapped?.times)
    }

    @Test
    fun `a stop without a valid id is dropped`() {
        assertNull(stop.copy(id = "970").toScheduledStopOrNull(0))
        val mapped = period.copy(stops = listOf(stop.copy(id = null), null, stop)).toServicePeriodOrNull()
        assertEquals(1, mapped?.stops?.size)
    }

    @Test
    fun `schedule keeps route and pattern`() {
        val schedule = listOf(period, null).toRouteSchedule(RouteId("1:minibusR24579"), PatternSuffix("0:01"))
        assertEquals(1, schedule.periods.size)
        assertEquals(PatternSuffix("0:01"), schedule.pattern)
    }
}
