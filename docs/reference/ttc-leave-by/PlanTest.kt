package ge.hackerman.bus

import ge.hackerman.bus.data.HomeStop
import ge.hackerman.bus.data.LiveState
import ge.hackerman.bus.data.ServicePeriod
import ge.hackerman.bus.data.TBILISI
import ge.hackerman.bus.data.departures
import ge.hackerman.bus.data.metres
import ge.hackerman.bus.data.parseClock
import ge.hackerman.bus.data.periodFor
import ge.hackerman.bus.data.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.ZonedDateTime

class PlanTest {
    private val wk = ServicePeriod(MONDAY, FRIDAY, setOf(LocalDate.of(2026, 9, 28)), listOf(7 * 60 + 55, 17 * 60 + 13, 17 * 60 + 31))
    private val we = ServicePeriod(SATURDAY, SUNDAY, emptySet(), listOf(8 * 60 + 26))
    private val r326 = HomeStop.routes.first { it.number == "326" }
    private val mon1701 = ZonedDateTime.of(2026, 9, 28, 17, 1, 0, 0, TBILISI)

    @Test fun clock() {
        assertEquals(475, parseClock("7:55"))
        assertEquals(1450, parseClock("24:10"))
        assertEquals(null, parseClock("x"))
    }

    @Test fun periodByDateThenWeekday() {
        assertEquals(wk, periodFor(listOf(wk, we), LocalDate.of(2026, 9, 28)))
        assertEquals(we, periodFor(listOf(wk, we), LocalDate.of(2026, 10, 4)))
        assertEquals(wk, periodFor(listOf(wk, we), LocalDate.of(2026, 10, 5)))
    }

    @Test fun departuresSpanIntoTomorrow() {
        val deps = departures(mapOf(r326.id to listOf(wk, we)), setOf("326"), mon1701)
        assertEquals(6, deps.size)
        assertEquals(17, deps.first { it.at.isAfter(mon1701) }.at.hour)
        assertTrue(departures(mapOf(r326.id to listOf(wk)), setOf("301"), mon1701).isEmpty())
    }

    @Test fun onlyNextDepartureGetsLiveState() {
        val deps = departures(mapOf(r326.id to listOf(wk)), setOf("326"), mon1701)
        val parked = plan(deps, mapOf("326" to 1), 5, mon1701)
        assertEquals(LiveState.WAITING, parked[0].live)
        assertEquals(LiveState.UNKNOWN, parked[1].live)
        assertEquals(17 * 60 + 8, parked[0].leaveBy.hour * 60 + parked[0].leaveBy.minute)

        val empty = plan(deps, mapOf("326" to 0), 5, mon1701.plusMinutes(4))
        assertEquals(LiveState.NOT_ARRIVED, empty[0].live)

        val noFeed = plan(deps, null, 5, mon1701)
        assertEquals(LiveState.UNKNOWN, noFeed[0].live)
    }

    @Test fun parkedBusDistance() {
        assertTrue(metres(41.7220535, 44.7031136, HomeStop.LAT, HomeStop.LON) < 5)
        assertTrue(metres(41.7204018, 44.7125778, HomeStop.LAT, HomeStop.LON) > 500)
    }
}
