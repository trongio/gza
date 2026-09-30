package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.predict.testing.PredictFixtures
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_326
import ge.hackerman.gza.core.predict.testing.tbilisi
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TimetableDeparturesTest {
    private val stop3569 = StopId("1:3569")
    private val stop970 = StopId("1:970")
    private val outbound = PredictFixtures.schedule("schedule/326-0-01-en.json", ROUTE_326, "0:01")
    private val inbound = PredictFixtures.schedule("schedule/326-1-01-en.json", ROUTE_326, "1:01")

    /** What the predictor lists without live data: yesterday, today and tomorrow, 1 min grace. */
    private fun RouteSchedule.upcoming(stopId: StopId, now: ZonedDateTime): List<ScheduledDeparture> {
        val today = now.toLocalDate()
        return departuresAt(stopId, listOf(today.minusDays(1), today, today.plusDays(1)))
            .filter { !it.scheduled.isBefore(now.minusMinutes(1)) }
    }

    private fun List<ScheduledDeparture>.firstTwo() = take(2).map { it.serviceDate to it.scheduled }

    private fun date(iso: String) = LocalDate.parse(iso)

    private fun minute(raw: String) = checkNotNull(ServiceMinute.parseOrNull(raw))

    private fun oneDay(vararg stops: ScheduledStop) = RouteSchedule(
        routeId = ROUTE_326,
        pattern = PatternSuffix("0:01"),
        periods = listOf(ServicePeriod(MONDAY, SUNDAY, emptySet(), stops.toList()))
    )

    private fun stop(id: String, position: Int, vararg times: String) =
        ScheduledStop(StopId(id), null, position, times.map(::minute))

    @Test
    fun `friday 23 59 lists friday 24 05 then saturday service`() {
        val rows = outbound.upcoming(stop3569, tbilisi("2026-10-02T23:59:00"))
        assertEquals(
            listOf(
                date("2026-10-02") to tbilisi("2026-10-03T00:05:00"),
                date("2026-10-03") to tbilisi("2026-10-03T09:31:00")
            ),
            rows.firstTwo()
        )
    }

    @Test
    fun `saturday 00 03 lists yesterday's 24 05 exactly once`() {
        val rows = outbound.upcoming(stop3569, tbilisi("2026-10-03T00:03:00"))
        assertEquals(1, rows.count { it.scheduled == tbilisi("2026-10-03T00:05:00") })
        assertEquals(
            listOf(
                date("2026-10-02") to tbilisi("2026-10-03T00:05:00"),
                date("2026-10-03") to tbilisi("2026-10-03T09:31:00")
            ),
            rows.firstTwo()
        )
    }

    @Test
    fun `sunday 23 59 lists sunday 24 05 then monday service`() {
        val rows = outbound.upcoming(stop3569, tbilisi("2026-10-04T23:59:00"))
        assertEquals(
            listOf(
                date("2026-10-04") to tbilisi("2026-10-05T00:05:00"),
                date("2026-10-05") to tbilisi("2026-10-05T09:05:00")
            ),
            rows.firstTwo()
        )
    }

    @Test
    fun `sunday 23 59 past the listed week falls back by weekday`() {
        val rows = outbound.upcoming(stop3569, tbilisi("2026-10-11T23:59:00"))
        assertEquals(
            listOf(
                date("2026-10-11") to tbilisi("2026-10-12T00:05:00"),
                date("2026-10-12") to tbilisi("2026-10-12T09:05:00")
            ),
            rows.firstTwo()
        )
    }

    @Test
    fun `monday 00 10 drops sunday's 00 05`() {
        val rows = outbound.upcoming(stop3569, tbilisi("2026-10-05T00:10:00"))
        assertEquals(tbilisi("2026-10-05T09:05:00"), rows.first().scheduled)
    }

    @Test
    fun `23 59 to 00 10 synthetic boundary`() {
        val schedule = oneDay(stop("1:1", 1, "23:59", "24:05", "24:10"), stop("1:2", 2, "23:59"))
        val times = { now: String -> schedule.upcoming(StopId("1:1"), tbilisi(now)).map { it.scheduled } }

        assertEquals(
            listOf("2026-10-05T23:59", "2026-10-06T00:05", "2026-10-06T00:10").map { tbilisi("$it:00") },
            times("2026-10-05T23:58:00").take(3)
        )
        assertEquals(
            listOf("2026-10-05T23:59", "2026-10-06T00:05", "2026-10-06T00:10").map { tbilisi("$it:00") },
            times("2026-10-06T00:00:00").take(3)
        )
        assertEquals(
            listOf("2026-10-06T00:10", "2026-10-06T23:59").map { tbilisi("$it:00") },
            times("2026-10-06T00:07:00").take(2)
        )
    }

    @Test
    fun `last stop of a pattern gives no rows but is still served`() {
        val dates = (0L..6L).map { date("2026-09-28").plusDays(it) }
        assertTrue(inbound.departuresAt(stop970, dates).isEmpty())
        assertTrue(inbound.servesStop(stop970))
    }

    @Test
    fun `first stop rows are marked as such, later stops are not`() {
        val rows = outbound.departuresAt(stop970, listOf(date("2026-09-28")))
        assertEquals(49, rows.size)
        assertTrue(rows.all { it.atFirstStop })
        assertTrue(outbound.departuresAt(stop3569, listOf(date("2026-09-28"))).none { it.atFirstStop })
    }

    @Test
    fun `a loop lists a stop twice and only the first listing is the first stop`() {
        val stops = (1..30).map { position ->
            val id = if (position == 20) "1:1" else "1:$position"
            stop(id, position, "8:00")
        }
        val rows = oneDay(*stops.toTypedArray()).departuresAt(StopId("1:1"), listOf(date("2026-09-28")))
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.atFirstStop })
    }

    @Test
    fun `a one stop period is its own first stop and is not skipped`() {
        val rows = oneDay(stop("1:1", 1, "8:00")).departuresAt(StopId("1:1"), listOf(date("2026-09-28")))
        assertEquals(1, rows.size)
        assertTrue(rows.single().atFirstStop)
    }

    @Test
    fun `empty service day gives no rows while the stop is still served`() {
        val weekdays = RouteSchedule(
            ROUTE_326,
            PatternSuffix("0:01"),
            listOf(ServicePeriod(MONDAY, FRIDAY, emptySet(), listOf(stop("1:1", 1, "8:00"), stop("1:2", 2, "8:05"))))
        )
        assertTrue(weekdays.departuresAt(StopId("1:1"), listOf(date("2026-10-03"), date("2026-10-04"))).isEmpty())
        assertTrue(weekdays.servesStop(StopId("1:1")))
        assertEquals(1, weekdays.departuresAt(StopId("1:1"), listOf(date("2026-10-05"))).size)
    }

    @Test
    fun `a stop no period lists is not served`() {
        assertEquals(false, outbound.servesStop(StopId("1:999999")))
        assertEquals(false, RouteSchedule(ROUTE_326, PatternSuffix("0:01"), emptyList()).servesStop(stop970))
    }

    @Test
    fun `rows carry route pattern stop and service date`() {
        val row = outbound.departuresAt(stop970, listOf(date("2026-09-28"))).first()
        assertEquals(
            ScheduledDeparture(
                ROUTE_326,
                PatternSuffix("0:01"),
                stop970,
                date("2026-09-28"),
                tbilisi("2026-09-28T07:55:00"),
                true
            ),
            row
        )
    }
}
