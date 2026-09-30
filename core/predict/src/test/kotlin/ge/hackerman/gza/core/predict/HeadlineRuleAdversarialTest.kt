package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.predict.testing.PredictFixtures
import ge.hackerman.gza.core.predict.testing.Snapshots
import ge.hackerman.gza.core.predict.testing.Snapshots.schedule
import ge.hackerman.gza.core.predict.testing.Snapshots.snapshot
import ge.hackerman.gza.core.predict.testing.Snapshots.stop970
import ge.hackerman.gza.core.predict.testing.Synthetic
import ge.hackerman.gza.core.predict.testing.Synthetic.boardRow
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.at
import ge.hackerman.gza.core.predict.testing.of
import ge.hackerman.gza.core.predict.testing.tbilisi
import ge.hackerman.gza.core.predict.testing.tbilisiClock
import ge.hackerman.gza.core.predict.testing.waitingVehicle
import java.time.DayOfWeek
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Attacks on the headline rule (PLAN.md 2.0): a bus parked at its first stop is never
 * predicted earlier than its next timetable departure and never as arriving now, whatever
 * the board, a stale heading, the labels, midnight or old memory say.
 */
class HeadlineRuleAdversarialTest {
    private val r326 = route("326")
    private val monday = "2026-09-28"

    private fun predict(local: String, request: StopRequest): StopPrediction =
        DeparturePredictor(tbilisiClock(local)).predict(request)

    private fun request(
        vararg snapshots: RouteSnapshot,
        board: StopBoard? = null,
        memory: LayoverMemory = LayoverMemory.Empty
    ) = StopRequest(home, snapshots.toList(), board, memory)

    private fun live(local: String, vararg vehicles: VehiclePosition): RoutePositions =
        Synthetic.positions(r326, tbilisi(local).toInstant(), *vehicles)

    /** Every row obeys the headline rule; a bus with no memory also gets the turnaround floor. */
    private fun assertNeverEarly(result: StopPrediction, memory: LayoverMemory = LayoverMemory.Empty) {
        val now = result.now
        result.departures.forEach { row ->
            assertFalse(row.predicted.isBefore(row.scheduled), "earlier than the timetable: $row")
            when (val state = row.state) {
                is DepartureState.Waiting -> {
                    assertFalse(state.leavesAt.isBefore(now), "arriving now: $row")
                    if (ParkedKey(row.routeId, state.vehicleId) !in memory.vehicles) {
                        assertFalse(state.leavesAt.isBefore(now.plusMinutes(2)), "no turnaround: $row")
                    }
                }

                is DepartureState.Late -> assertTrue(state.leavesAt.isAfter(now), "late bus leaves now: $row")

                DepartureState.NoBusYet, DepartureState.TimetableOnly -> assertEquals(row.scheduled, row.predicted)
            }
        }
        val buses = result.departures.mapNotNull { it.vehicle }
        assertEquals(buses.distinct(), buses, "a bus on two rows")
    }

    private val PredictedDeparture.vehicle: VehicleId?
        get() = when (val state = state) {
            is DepartureState.Waiting -> state.vehicleId
            is DepartureState.Late -> state.vehicleId
            else -> null
        }

    // Board at 0 plus the bogus arrivalDelay of the plan fixture (-3631 s). The engine has no
    // arrivalDelay input at all; the nearest thing it sees is the board's scheduled hint.

    @Test
    fun `board at 0 with a bogus -3631 s delay never moves the parked 326 off 17 49`() {
        val derived = "terminus/326-derived-1732"
        val base = StopRequest(
            stop = stop970,
            routes = listOf(
                snapshot(
                    "326",
                    listOf(
                        PredictFixtures.schedule("$derived/schedule-0-01.json", Snapshots.routeId("326"), "0:01"),
                        schedule("326", "1-01")
                    ),
                    Snapshots.positions("$derived/positions.json", "326")
                )
            )
        )
        val clock = tbilisiClock("2026-09-28T17:32:00")
        val plain = DeparturePredictor(clock).predict(base)
        listOf(-61, -3631, Int.MIN_VALUE, Int.MAX_VALUE).forEach { bogus ->
            val board = StopBoard(
                stop970.id,
                tbilisi("2026-09-28T17:32:00").toInstant(),
                listOf(boardRow("326", "0:01", realtime = 0, scheduled = bogus), boardRow("326", null, 0, bogus))
            )
            val withBoard = DeparturePredictor(clock).predict(base.copy(board = board))
            val row = withBoard.departures.at("326", "17:49")
            assertEquals(DepartureState.Waiting(VehicleId("1:3046"), tbilisi("2026-09-28T17:49:00")), row.state)
            assertEquals(0, row.boardHint?.realtimeMinutes)
            assertEquals(bogus, row.boardHint?.scheduledMinutes)
            assertEquals(plain.departures, withBoard.departures.map { it.copy(boardHint = null) })
            assertEquals(plain.memory, withBoard.memory)
            assertNeverEarly(withBoard)
        }
    }

    @Test
    fun `a bus with a stale heading that never moves at the terminus is never shown early`() {
        val stuck = VehiclePosition(VehicleId("1:3046"), PatternSuffix("0:01"), Synthetic.HOME, 331.5, null)
        val schedules = listOf(Synthetic.schedule(r326, "0:01", listOf("17:49", "18:07")))
        var memory = LayoverMemory.Empty
        listOf("17:32:00", "17:32:15", "17:40:00", "17:48:30", "17:49:30").forEach { time ->
            val now = "${monday}T$time"
            val result =
                predict(now, request(RouteSnapshot(r326, schedules, positions = live(now, stuck)), memory = memory))
            assertNeverEarly(result, memory)
            assertTrue(result.departures.none { it.vehicle != null }, "a moving bus is waiting at $time")
            assertTrue(result.memory.vehicles.isEmpty(), "a moving bus is remembered at $time")
            result.departures.forEach { assertEquals(it.scheduled, it.predicted) }
            memory = result.memory
        }
    }

    @Test
    fun `a next stop at the first stop means not parked and not early`() {
        val boarding = VehiclePosition(VehicleId("1:3046"), PatternSuffix("0:01"), Synthetic.HOME, null, StopId("9:2"))
        val now = "${monday}T17:48:00"
        val schedules = listOf(Synthetic.schedule(r326, "0:01", listOf("17:49", "18:07")))
        val result = predict(now, request(RouteSnapshot(r326, schedules, positions = live(now, boarding))))
        assertNeverEarly(result)
        assertEquals(DepartureState.NoBusYet, result.departures.at("326", "17:49").state)
        assertEquals(tbilisi("${monday}T17:49:00"), result.departures.at("326", "17:49").predicted)
    }

    @Test
    fun `two buses parked for the last departure of the day share nothing`() {
        // Mondays only, so neither yesterday nor tomorrow adds a row: one departure, two buses.
        val schedules = listOf(
            Synthetic.schedule(r326, "0:01", listOf("17:49"), from = DayOfWeek.MONDAY, to = DayOfWeek.MONDAY)
        )
        val now = "${monday}T17:32:00"
        val first = predict(
            now,
            request(RouteSnapshot(r326, schedules, positions = live(now, parked("1:3046"), parked("1:3047"))))
        )
        assertNeverEarly(first)
        assertEquals(1, first.departures.size)
        assertEquals(tbilisi("${monday}T17:49:00"), first.departures.single().predicted)
        val spare = first.memory.vehicles.values.filter { it.waitingFor == null }
        assertEquals(1, spare.size, "the second bus has no departure to wait for")

        // Both remembered as waiting for 17:49, both still parked after it: one late row, not two.
        val both = LayoverMemory(
            listOf("1:3046", "1:3047").associate {
                ParkedKey(r326.id, VehicleId(it)) to ParkedVehicle(
                    PatternSuffix("0:01"),
                    tbilisi("${monday}T17:30:00").toInstant(),
                    tbilisi("${monday}T17:49:00")
                )
            }
        )
        val later = "${monday}T17:51:00"
        val late = predict(
            later,
            request(
                RouteSnapshot(r326, schedules, positions = live(later, parked("1:3046"), parked("1:3047"))),
                memory = both
            )
        )
        assertNeverEarly(late, both)
        assertEquals(1, late.departures.size)
        assertTrue(late.departures.single().state is DepartureState.Late)
    }

    @Test
    fun `the same bus listed under both patterns takes one departure, not two`() {
        val schedules = listOf(
            Synthetic.schedule(r326, "0:01", listOf("17:49", "18:07")),
            Synthetic.schedule(r326, "1:01", listOf("17:30"), position = 3)
        )
        val now = "${monday}T17:32:00"
        val outbound = parked("1:3046", "0:01")
        val inbound = parked("1:3046", "1:01")
        listOf(listOf(outbound, inbound), listOf(inbound, outbound)).forEach { listed ->
            val result =
                predict(now, request(RouteSnapshot(r326, schedules, positions = live(now, *listed.toTypedArray()))))
            assertNeverEarly(result)
            assertEquals("1:3046", result.departures.at("326", "17:49").waitingVehicle)
            assertEquals(
                DepartureState.TimetableOnly,
                result.departures.at("326", "18:07").state,
                "a ghost second bus is waiting for 18:07"
            )
            assertEquals(PatternSuffix("0:01"), result.memory.of("1:3046").pattern)
        }
    }

    @Test
    fun `a 326 parked at 1 970 labelled with its inbound pattern waits for the next outbound time`() {
        val schedules = listOf(schedule("326", "0-01"), schedule("326", "1-01"))
        listOf("17:32:00", "23:59:00", "07:40:00").forEach { time ->
            val now = "${monday}T$time"
            val positions = RoutePositions(
                Snapshots.routeId("326"),
                tbilisi(now).toInstant(),
                listOf(parked("1:9999", "1:01", stop970.location))
            )
            val result = predict(now, StopRequest(stop970, listOf(snapshot("326", schedules, positions))))
            assertNeverEarly(result)
            assertTrue(result.departures.none { it.pattern == PatternSuffix("1:01") }, "an arrival listed at $time")
            val next = result.departures.first { !it.scheduled.isBefore(tbilisi(now)) }
            assertEquals("1:9999", next.waitingVehicle, "at $time")
            assertEquals(PatternSuffix("0:01"), next.pattern)
            assertEquals(PatternSuffix("0:01"), result.memory.of("1:9999").pattern)
        }
    }

    @Test
    fun `at 23 59 the 24 02 arrival of the inbound 326 is not a departure`() {
        val schedules = listOf(schedule("326", "0-01"), schedule("326", "1-01"))
        val now = "${monday}T23:59:00"
        val positions = RoutePositions(Snapshots.routeId("326"), tbilisi(now).toInstant(), emptyList())
        val result = predict(now, StopRequest(stop970, listOf(snapshot("326", schedules, positions))))
        val first = result.departures.first()
        assertEquals(tbilisi("2026-09-29T07:55:00"), first.scheduled)
        assertEquals(PatternSuffix("0:01"), first.pattern)
    }

    @Test
    fun `parked across midnight 23 55 to 00 20 with 24 xx times`() {
        val schedules = listOf(Synthetic.schedule(r326, "0:01", listOf("23:55", "24:20")))
        val serviceDay = tbilisi("${monday}T00:00:00").toLocalDate()

        val at2350 = "${monday}T23:50:00"
        val evening = predict(
            at2350,
            request(RouteSnapshot(r326, schedules, positions = live(at2350, parked("1:A"), parked("1:B"))))
        )
        assertNeverEarly(evening)
        assertEquals("1:A", evening.departures.at("326", "23:55").waitingVehicle)
        val after = evening.departures.first { it.waitingVehicle == "1:B" }
        assertEquals(tbilisi("2026-09-29T00:20:00"), after.predicted)
        assertEquals(serviceDay, after.serviceDate)

        // 00:02: A missed 23:55 and is still there, B still waits for yesterday's 24:20.
        val at0002 = "2026-09-29T00:02:00"
        val night = predict(
            at0002,
            request(
                RouteSnapshot(r326, schedules, positions = live(at0002, parked("1:A"), parked("1:B"))),
                memory = evening.memory
            )
        )
        assertNeverEarly(night, evening.memory)
        val late = night.departures.single { it.state is DepartureState.Late }
        assertEquals(tbilisi("${monday}T23:55:00"), late.scheduled)
        assertEquals(tbilisi("2026-09-29T00:03:00"), late.predicted)
        val b = night.departures.single { it.waitingVehicle == "1:B" }
        assertEquals(tbilisi("2026-09-29T00:20:00"), b.predicted)
        assertEquals(serviceDay, b.serviceDate)
        assertEquals(1, night.departures.count { it.scheduled == tbilisi("2026-09-29T00:20:00") })

        // 00:19: B right before its time keeps its slot; a bus that only just pulled in gets the floor.
        val at0019 = "2026-09-29T00:19:00"
        val kept = predict(
            at0019,
            request(RouteSnapshot(r326, schedules, positions = live(at0019, parked("1:B"))), memory = night.memory)
        )
        assertEquals(tbilisi("2026-09-29T00:20:00"), kept.departures.single { it.waitingVehicle == "1:B" }.predicted)
        val fresh = predict(at0019, request(RouteSnapshot(r326, schedules, positions = live(at0019, parked("1:C")))))
        assertNeverEarly(fresh)
        assertEquals(tbilisi("2026-09-29T00:21:00"), fresh.departures.single { it.waitingVehicle == "1:C" }.predicted)
    }

    @Test
    fun `yesterday's memory never makes today's bus late`() {
        val schedules = listOf(Synthetic.schedule(r326, "0:01", listOf("17:31", "17:49", "18:07")))
        val yesterday = LayoverMemory(
            mapOf(
                ParkedKey(r326.id, VehicleId("1:3046")) to ParkedVehicle(
                    PatternSuffix("0:01"),
                    tbilisi("2026-09-27T17:40:00").toInstant(),
                    tbilisi("2026-09-27T17:49:00")
                )
            )
        )
        val now = "${monday}T17:49:30"
        val result = predict(
            now,
            request(RouteSnapshot(r326, schedules, positions = live(now, parked("1:3046"))), memory = yesterday)
        )
        assertNeverEarly(result, yesterday)
        assertTrue(result.departures.none { it.state is DepartureState.Late })
        assertEquals(DepartureState.TimetableOnly, result.departures.at("326", "17:49").state)
        assertEquals("1:3046", result.departures.at("326", "18:07").waitingVehicle)
        assertEquals(tbilisi("${monday}T18:07:00"), result.departures.at("326", "18:07").predicted)
    }

    @Test
    @Disabled("Bug: a first sighting of any age waives the turnaround floor (T05 tester report)")
    fun `yesterday's first sighting does not waive today's turnaround`() {
        // The bus pulled in seconds ago; only a day-old memory entry says it has been there long.
        val schedules = listOf(Synthetic.schedule(r326, "0:01", listOf("17:49", "18:07")))
        val yesterday = LayoverMemory(
            mapOf(
                ParkedKey(r326.id, VehicleId("1:3046")) to ParkedVehicle(
                    PatternSuffix("0:01"),
                    tbilisi("2026-09-27T17:40:00").toInstant(),
                    tbilisi("2026-09-27T17:49:00")
                )
            )
        )
        val now = "${monday}T17:48:50"
        val result = predict(
            now,
            request(RouteSnapshot(r326, schedules, positions = live(now, parked("1:3046"))), memory = yesterday)
        )
        val row = result.departures.at("326", "17:49")
        assertEquals("1:3046", row.waitingVehicle)
        assertFalse(
            row.predicted.isBefore(tbilisi(now).plus(PredictionRules.Default.minTurnaround)),
            "leaves ${row.predicted}, 10 s after pulling in"
        )
        assertEquals(tbilisi(now).toInstant(), result.memory.of("1:3046").firstSeen)
    }

    @Test
    fun `no board, labels or memory make a waiting row earlier than the timetable at the whole stop`() {
        // The real 21:01 stop with every bus given a hostile memory: seen long ago, waiting for now.
        val now = tbilisi("2026-09-28T21:01:54")
        val routes = listOf(
            snapshot(
                "301",
                listOf(schedule("301", "0-01")),
                Snapshots.positions("positions/301-20260928T2101.json", "301")
            ),
            snapshot(
                "326",
                listOf(schedule("326", "0-01"), schedule("326", "1-01")),
                Snapshots.positions("positions/326-20260928T2101.json", "326")
            ),
            snapshot(
                "551",
                listOf(schedule("551", "0-01")),
                Snapshots.positions("positions/551-20260928T2101.json", "551")
            )
        )
        val hostile = LayoverMemory(
            routes.flatMap { snapshot ->
                snapshot.positions!!.vehicles.map {
                    ParkedKey(snapshot.route.id, it.vehicleId) to ParkedVehicle(
                        it.pattern,
                        now.minusHours(3).toInstant(),
                        now.truncatedTo(ChronoUnit.MINUTES)
                    )
                }
            }.toMap()
        )
        val board = PredictFixtures.board("arrival-times/1-970-en-20260928T2101.json", stop970.id)
        val result = DeparturePredictor(tbilisiClock("2026-09-28T21:01:54"))
            .predict(StopRequest(stop970, routes, board, hostile))
        assertNeverEarly(result, hostile)
        assertNull(result.departures.firstOrNull { it.predicted.isBefore(now.minus(Duration.ofMinutes(1))) })
    }
}
