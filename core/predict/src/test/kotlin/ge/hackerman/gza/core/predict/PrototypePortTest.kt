package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.predict.testing.tbilisi
import java.time.Clock
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every case of the ttc-leave-by prototype (`docs/reference/ttc-leave-by/PlanTest.kt`), with
 * the same inputs, on this engine. The prototype's periods are one-stop lists; here `1:970`
 * gets a second stop after it so it is the first stop of the pattern and not also its last.
 */
class PrototypePortTest {
    private val home = LatLon(41.722055, 44.703114)
    private val homeStop = Stop(StopId("1:970"), "970", "Politkovskaya St", home, TransportKind.BUS)
    private val r326 = Route(RouteId("1:R97493"), "326", null, null, TransportKind.BUS)
    private val r301 = Route(RouteId("1:R29981"), "301", null, null, TransportKind.BUS)

    private fun stops(vararg times: String): List<ScheduledStop> {
        val minutes = times.map { checkNotNull(ServiceMinute.parseOrNull(it)) }
        return listOf(
            ScheduledStop(homeStop.id, null, 1, minutes),
            ScheduledStop(StopId("1:969"), null, 2, minutes)
        )
    }

    private val wk = ServicePeriod(MONDAY, FRIDAY, setOf(LocalDate.of(2026, 9, 28)), stops("7:55", "17:13", "17:31"))
    private val we = ServicePeriod(SATURDAY, SUNDAY, emptySet(), stops("8:26"))
    private val mon1701 = tbilisi("2026-09-28T17:01:00")

    private fun schedule(vararg periods: ServicePeriod) =
        RouteSchedule(r326.id, PatternSuffix("0:01"), periods.toList())

    private fun predict(now: ZonedDateTime, vararg snapshots: RouteSnapshot) =
        DeparturePredictor(Clock.fixed(now.toInstant(), ZoneOffset.UTC))
            .predict(StopRequest(homeStop, snapshots.toList()))
            .departures

    private fun positions(now: ZonedDateTime, vararg vehicles: VehiclePosition) =
        RoutePositions(r326.id, now.toInstant(), vehicles.toList())

    private val parkedAtHome =
        VehiclePosition(VehicleId("1:3046"), PatternSuffix("0:01"), LatLon(41.7220535, 44.7031136), null, null)

    @Test
    fun clock() {
        assertEquals(475, ServiceMinute.parseOrNull("7:55")?.minutes)
        assertEquals(1450, ServiceMinute.parseOrNull("24:10")?.minutes)
        assertNull(ServiceMinute.parseOrNull("x"))
    }

    @Test
    fun periodByDateThenWeekday() {
        assertSame(wk, listOf(wk, we).periodFor(LocalDate.of(2026, 9, 28)))
        assertSame(we, listOf(wk, we).periodFor(LocalDate.of(2026, 10, 4)))
        assertSame(wk, listOf(wk, we).periodFor(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun departuresSpanIntoTomorrow() {
        val rows = schedule(
            wk,
            we
        ).departuresAt(homeStop.id, listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29)))
        assertEquals(6, rows.size)

        val predicted = predict(mon1701, RouteSnapshot(r326, listOf(schedule(wk, we))))
        assertEquals(17, predicted.first { it.scheduled.isAfter(mon1701) }.scheduled.hour)

        // Prototype: 301 enabled, but only 326 has a timetable.
        val result = DeparturePredictor(Clock.fixed(mon1701.toInstant(), ZoneOffset.UTC))
            .predict(StopRequest(homeStop, listOf(RouteSnapshot(r301, emptyList()))))
        assertTrue(result.departures.isEmpty())
        assertEquals(listOf(r301.id), result.missingTimetables)
    }

    @Test
    fun onlyNextDepartureGetsLiveState() {
        val parked =
            predict(mon1701, RouteSnapshot(r326, listOf(schedule(wk)), positions = positions(mon1701, parkedAtHome)))
        assertEquals(DepartureState.Waiting(VehicleId("1:3046"), tbilisi("2026-09-28T17:13:00")), parked[0].state)
        assertEquals(DepartureState.TimetableOnly, parked[1].state)
        assertEquals(tbilisi("2026-09-28T17:08:00"), parked[0].leaveBy(walkMinutes = 5, bufferMinutes = 0))

        val at1705 = mon1701.plusMinutes(4)
        val empty = predict(at1705, RouteSnapshot(r326, listOf(schedule(wk)), positions = positions(at1705)))
        assertEquals(DepartureState.NoBusYet, empty[0].state)

        val noFeed = predict(mon1701, RouteSnapshot(r326, listOf(schedule(wk)), positions = null))
        assertEquals(DepartureState.TimetableOnly, noFeed[0].state)
    }

    @Test
    fun parkedBusDistance() {
        assertTrue(LatLon(41.7220535, 44.7031136).metersTo(home) < 5)
        val far = LatLon(41.7204018, 44.7125778).metersTo(home)
        assertTrue(far > 500)
        assertTrue(far > PredictionRules.Default.layoverRadiusMeters)
    }
}
