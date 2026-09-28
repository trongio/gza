package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransitLeg
import ge.hackerman.gza.core.model.TripPlan
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.distanceMeters
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * The gateway's known lies, from real captures (and two labelled derived ones), kept as
 * hints through the whole client. These are the facts `:core:predict` builds on; the
 * headline one is a bus parked at its terminus while the board says 0.
 */
class KnownBadCasesTest {
    private val gateway = FixtureGateway()
    private val stop970 = StopId("1:970")
    private val stop970At = LatLon(41.722055, 44.703114)
    private val outbound = PatternSuffix("0:01")
    private val both = listOf(outbound, PatternSuffix("1:01"))

    @AfterEach
    fun tearDown() = gateway.close()

    private fun board(path: String): StopBoard = gateway.serve(path).let { meta ->
        runBlocking { gateway.client(clockAt = meta.recordedAt).arrivalBoard(stop970, Language.EN) }
    }

    private fun positions(path: String, route: RouteId): RoutePositions = gateway.serve(path).let { meta ->
        runBlocking { gateway.client(clockAt = meta.recordedAt).positions(route, both) }
    }

    private fun schedule(path: String, route: RouteId): RouteSchedule {
        gateway.serve(path)
        return runBlocking { gateway.client().schedule(route, outbound, Language.EN) }
    }

    private fun plan(path: String): TripPlan {
        gateway.serve(path)
        val request = TripRequest(stop970At, LatLon(41.6934, 44.8015))
        return runBlocking { gateway.client().plan(request, Language.EN) }
    }

    /** First weekday departure from 1:970 at or after [at], in Tbilisi time. */
    private fun nextDeparture(schedule: RouteSchedule, at: Instant): Instant {
        val weekdays = schedule.periods.single { it.fromDay == DayOfWeek.MONDAY }
        val serviceDate = LocalDate.of(2026, 9, 28)
        val first = weekdays.stops.first { it.stopId == stop970 }
        return first.times.map { it.atDate(serviceDate).toInstant() }.first { !it.isBefore(at) }
    }

    @Test
    fun `board says 0 at the terminus while a 551 is parked there`() {
        val route = RouteId("1:minibusR24579")
        val dir = "terminus/551-20260928T2043"
        val board = board("$dir/arrival-times.json")
        assertEquals(0, board.arrivals.single { it.routeShortName == "551" }.realtimeMinutesHint)

        val parked = positions("$dir/positions.json", route).vehicles.single { it.vehicleId == VehicleId("1:981") }
        assertTrue(distanceMeters(parked.location, stop970At) < 5)
        assertEquals(outbound, parked.pattern)
        assertNull(parked.headingDegrees)
        assertNull(parked.nextStopId)

        // The board's "0" is 20:43; the bus actually leaves at 20:47.
        val next = nextDeparture(schedule("$dir/schedule-0-01.json", route), board.fetchedAt)
        assertEquals(Instant.parse("2026-09-28T16:47:00Z"), next)
    }

    @Test
    fun `a second real capture has two 301s parked while the board says 0`() {
        val route = RouteId("1:R29981")
        val dir = "terminus/301-20260928T2102"
        val board = board("$dir/arrival-times.json")
        assertEquals(0, board.arrivals.single { it.routeShortName == "301" }.realtimeMinutesHint)
        val parked = positions("$dir/positions.json", route).vehicles.filter {
            it.headingDegrees == null && distanceMeters(it.location, stop970At) < 5
        }
        assertTrue(parked.size >= 2, "parked: $parked")
        val next = nextDeparture(schedule("$dir/schedule-0-01.json", route), board.fetchedAt)
        assertTrue(next.isAfter(board.fetchedAt))
    }

    @Test
    fun `derived 326 layover fixture keeps the 0 minute hint`() {
        val route = RouteId("1:R97493")
        val dir = "terminus/326-derived-1732"
        val board = board("$dir/arrival-times.json")
        assertEquals(Instant.parse("2026-09-28T13:32:00Z"), board.fetchedAt)
        assertEquals(0, board.arrivals.single { it.routeShortName == "326" }.realtimeMinutesHint)

        val parked = positions("$dir/positions.json", route).vehicles.single { it.vehicleId == VehicleId("1:3046") }
        assertTrue(distanceMeters(parked.location, stop970At) < 5)
        assertNull(parked.headingDegrees)
        assertNull(parked.nextStopId)

        val next = nextDeparture(schedule("$dir/schedule-0-01.json", route), board.fetchedAt)
        assertEquals(Instant.parse("2026-09-28T13:49:00Z"), next, "17:49 Tbilisi")
        assertEquals(Duration.ofMinutes(17), Duration.between(board.fetchedAt, next))
    }

    @Test
    fun `negative scheduled minutes survive as hints`() {
        val rows = board("terminus/551-20260928T2043/arrival-times.json").arrivals.associateBy { it.routeShortName }
        assertEquals(-76, rows.getValue("551").scheduledMinutesHint)
        assertEquals(-30, rows.getValue("326").scheduledMinutesHint)
        assertEquals(17, rows.getValue("326").realtimeMinutesHint)
    }

    @Test
    fun `null heading and null next stop are kept`() {
        val r551 = positions("terminus/551-20260928T2043/positions.json", RouteId("1:minibusR24579"))
        val bothNull = r551.vehicles.filter { it.headingDegrees == null && it.nextStopId == null }
        assertEquals(setOf("1:774", "1:985", "1:981"), bothNull.map { it.vehicleId.value }.toSet())
        val r301 = positions("positions/301-20260928T2044.json", RouteId("1:R29981"))
        assertTrue(r301.vehicles.isNotEmpty())
        assertTrue(r301.vehicles.all { it.headingDegrees == null || it.headingDegrees!! in 0.0..360.0 })
    }

    @Test
    fun `heading without next stop is kept`() {
        val vehicle = positions("terminus/551-20260928T2043/positions.json", RouteId("1:minibusR24579"))
            .vehicles.single { it.vehicleId == VehicleId("1:220") }
        assertNotNull(vehicle.headingDegrees)
        assertNull(vehicle.nextStopId)
    }

    @Test
    fun `plan keeps a bogus arrivalDelay as a raw hint`() {
        fun delays(plan: TripPlan) =
            plan.itineraries.flatMap { it.legs }.filterIsInstance<TransitLeg>().mapNotNull { it.arrivalDelayHint }
        val derived = delays(plan("plan/derived-arrival-delay-3676.json"))
        assertEquals(Duration.ofSeconds(3676), derived.first())
        val real = delays(plan("plan/leave-now-970-to-freedom-square-20260928T2043.json"))
        assertTrue(Duration.ofSeconds(4607) in real)
        assertTrue(Duration.ofSeconds(-3631) in real, "kept negative")
    }

    @Test
    fun `times past midnight`() {
        val schedule = schedule("schedule/326-0-01-en.json", RouteId("1:R97493"))
        val minutes = schedule.periods.first().stops.flatMap { stop -> stop.times.map { it.minutes } }
        assertTrue(1445 in minutes, "24:05")
        assertTrue(1448 in minutes, "24:08")
    }

    @Test
    fun `week rollover data is parsed as sent`() {
        val periods = schedule("schedule/551-0-01-en.json", RouteId("1:minibusR24579")).periods
        assertEquals(
            listOf(
                DayOfWeek.MONDAY to DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY to DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY to DayOfWeek.SUNDAY
            ),
            periods.map { it.fromDay to it.toDay }
        )
        assertEquals(
            (28..30).map {
                LocalDate.of(2026, 9, it)
            } + (1..2).map { LocalDate.of(2026, 10, it) },
            periods[0].serviceDates.toList()
        )
        assertEquals(setOf(LocalDate.of(2026, 10, 3)), periods[1].serviceDates)
        assertEquals(setOf(LocalDate.of(2026, 10, 4)), periods[2].serviceDates)
    }
}
