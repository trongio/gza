package ge.hackerman.gza.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.StopId
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A timetable goes through a real sync, Room and the repository unchanged: minute 0, 23:59,
 * 24:04, the largest service minute (47:59), a stop row with no times, a period with no rows,
 * a period with no service dates, and a loop that passes a stop twice.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleRoundTripTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val r326 = FixtureDomain.routeId("326")
    private val outbound = PatternSuffix("0:01")
    private val s970 = StopId(FixtureDomain.STOP_970)
    private val s969 = StopId("1:969")
    private val s824 = StopId("1:824")

    @After
    fun tearDown() = graph.db.close()

    private fun minutes(vararg raw: String) = raw.map { requireNotNull(ServiceMinute.parseOrNull(it)) { it } }

    private val weekdays = ServicePeriod(
        fromDay = DayOfWeek.MONDAY,
        toDay = DayOfWeek.FRIDAY,
        serviceDates = (0L..4L).map { LocalDate.parse("2026-09-28").plusDays(it) }.toSet(),
        stops = listOf(
            ScheduledStop(s970, name = null, position = 0, times = minutes("0:00", "23:59", "24:04", "47:59")),
            ScheduledStop(s969, name = null, position = 1, times = emptyList()),
            // A loop: back at the first stop at the end of the trip.
            ScheduledStop(s970, name = null, position = 2, times = minutes("24:30"))
        )
    )

    // A period the gateway lists with no stop rows at all.
    private val saturday = ServicePeriod(DayOfWeek.SATURDAY, DayOfWeek.SATURDAY, emptySet(), emptyList())

    // Runs, but never at 1:970.
    private val sunday = ServicePeriod(
        DayOfWeek.SUNDAY,
        DayOfWeek.SUNDAY,
        setOf(LocalDate.parse("2026-10-04")),
        listOf(ScheduledStop(s824, name = null, position = 5, times = minutes("10:00")))
    )

    private val custom = RouteSchedule(r326, outbound, listOf(weekdays, saturday, sunday))

    private suspend fun syncCustom() {
        graph.gateway.scheduleOverride = { id, pattern ->
            if (pattern == outbound) custom else FixtureDomain.schedule(id, pattern)
        }
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRoute(r326))
    }

    @Test
    fun `boundary minutes, an empty stop row and an empty period round trip exactly`() = runBlocking {
        syncCustom()
        assertEquals(custom, graph.routes.observeSchedule(r326, outbound).first())
    }

    @Test
    fun `the parsed boundaries are the minutes expected`() {
        assertEquals(listOf(0, 1439, 1444, 2879), minutes("0:00", "23:59", "24:04", "47:59").map { it.minutes })
        assertNull(ServiceMinute.parseOrNull("48:00"))
    }

    @Test
    fun `at a stop, a loop gives both rows in trip order and skipping periods are listed empty`() = runBlocking {
        syncCustom()
        val atStop = graph.routes.observeSchedulesAtStop(s970).first()
        val schedule = atStop.single { it.routeId == r326 && it.pattern == outbound }
        assertEquals(3, schedule.periods.size)
        val (mon, sat, sun) = schedule.periods
        assertEquals(listOf(weekdays.stops[0], weekdays.stops[2]), mon.stops)
        assertEquals(weekdays.serviceDates, mon.serviceDates)
        assertEquals(emptyList<ScheduledStop>(), sat.stops)
        assertEquals(emptySet<LocalDate>(), sat.serviceDates)
        assertEquals(emptyList<ScheduledStop>(), sun.stops)
        assertEquals(DayOfWeek.SUNDAY, sun.fromDay)
    }

    @Test
    fun `a stop only one period serves lists the others empty, a stop no period serves lists nothing`() = runBlocking {
        syncCustom()
        val at824 = graph.routes.observeSchedulesAtStop(s824).first()
            .single { it.routeId == r326 && it.pattern == outbound }
        assertEquals(listOf(0, 0, 1), at824.periods.map { it.stops.size })
        assertEquals(minutes("10:00"), at824.periods[2].stops.single().times)

        val nowhere = graph.routes.observeSchedulesAtStop(StopId("1:404404")).first()
        assertEquals(emptyList<RouteSchedule>(), nowhere)
    }

    @Test
    fun `the other pattern is untouched by the custom one`() = runBlocking {
        syncCustom()
        val inbound = PatternSuffix("1:01")
        val stored = graph.routes.observeSchedule(r326, inbound).first()
        assertNotNull(stored)
        val expected = FixtureDomain.schedule(r326, inbound)
        assertEquals(
            expected.periods.map { period -> period.stops.map { it.stopId to it.times } },
            stored!!.periods.map { period -> period.stops.map { it.stopId to it.times } }
        )
    }
}
