package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.predict.testing.Synthetic.HOME
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.moving
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.positions
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.clock
import ge.hackerman.gza.core.predict.testing.tbilisi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * A remembered parked bus left out of a live poll within [PredictionRules.memoryDropout] of
 * when it was last seen holds its Waiting or Late row, without refreshing lastSeen. These
 * tests push that hold against competing buses, passing row times, offline routes, reused ids
 * and midnight, and check the headline rule on every poll: a parked bus never leaves before
 * max(its timetable time, its first sighting + 2 min), never before now, one row per bus.
 */
class HeldThroughDropoutAdversarialTest {
    private val r326 = route("326")
    private val r551 = route("551", id = "1:minibusR24579")
    private val out = PatternSuffix("0:01")
    private val day = "2026-09-28"
    private val times = listOf("17:31", "17:49", "18:07")

    private fun at(time: String): ZonedDateTime = if ('T' in time) tbilisi(time) else tbilisi("${day}T$time")

    private fun instant(time: String): Instant = at(time).toInstant()

    private fun snapshot(
        route: Route,
        now: ZonedDateTime,
        vehicles: List<VehiclePosition>?,
        outbound: List<String> = times,
        fetchedAt: Instant = now.toInstant()
    ) = RouteSnapshot(
        route,
        listOf(
            schedule(route, "0:01", outbound),
            schedule(route, "1:01", listOf("17:40", "18:20"), position = 2)
        ),
        positions = vehicles?.let { positions(route, fetchedAt, *it.toTypedArray()) }
    )

    private fun predict(now: ZonedDateTime, memory: LayoverMemory, vararg snapshots: RouteSnapshot): StopPrediction =
        DeparturePredictor(now.toInstant().clock()).predict(StopRequest(home, snapshots.toList(), memory = memory))

    private fun poll(
        now: ZonedDateTime,
        memory: LayoverMemory,
        vehicles: List<VehiclePosition>?,
        outbound: List<String> = times
    ): StopPrediction = predict(now, memory, snapshot(r326, now, vehicles, outbound))

    private fun entry(firstSeen: String, waitingFor: String?, lastSeen: String) =
        ParkedVehicle(out, instant(firstSeen), waitingFor?.let(::at), instant(lastSeen))

    private fun memory(vararg entries: Pair<ParkedKey, ParkedVehicle>) = LayoverMemory(entries.toMap())

    private fun key(route: Route, vehicle: String) = ParkedKey(route.id, VehicleId(vehicle))

    private fun StopPrediction.rowAt(
        time: ZonedDateTime,
        routeId: RouteId = r326.id,
        pattern: PatternSuffix = out
    ): PredictedDeparture? =
        departures.singleOrNull { it.routeId == routeId && it.pattern == pattern && it.scheduled.isEqual(time) }

    private fun StopPrediction.rowsOf(vehicle: String, routeId: RouteId = r326.id): List<PredictedDeparture> =
        departures.filter { it.routeId == routeId && it.state.vehicle() == vehicle }

    private fun DepartureState.vehicle(): String? = when (this) {
        is DepartureState.Waiting -> vehicleId.value
        is DepartureState.Late -> vehicleId.value
        else -> null
    }

    /** The headline rule on one poll, with [firstSeen] keyed by route and vehicle. */
    private fun StopPrediction.assertHeadline(firstSeen: Map<ParkedKey, Instant>, context: String) {
        val shown = departures.mapNotNull { row ->
            val leavesAt = when (val state = row.state) {
                is DepartureState.Waiting -> {
                    assertFalse(state.leavesAt.isBefore(row.scheduled), "left before its timetable, $context: $row")
                    state.leavesAt
                }

                is DepartureState.Late -> {
                    assertTrue(state.leavesAt.isAfter(now), "late but leaving now, $context: $row")
                    assertEquals(Duration.between(row.scheduled, state.leavesAt), state.lateBy, "$context: $row")
                    state.leavesAt
                }

                else -> return@mapNotNull null
            }
            assertEquals(leavesAt, row.predicted, "$context: $row")
            assertFalse(leavesAt.isBefore(now), "arriving now, $context: $row")
            val bus = ParkedKey(row.routeId, VehicleId(checkNotNull(row.state.vehicle())))
            firstSeen[bus]?.let {
                assertFalse(leavesAt.toInstant().isBefore(it.plus(TURNAROUND)), "turnaround waived, $context: $row")
            }
            bus
        }
        assertEquals(shown.distinct(), shown, "one row per bus, $context")
    }

    @Nested
    inner class AnotherBusPullsIn {
        @Test
        fun `a bus pulling in behind a held bus gets the next row, not the held one`() {
            val start = memory(key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:48:30"))
            val firstSeen = mapOf(key(r326, "1:1") to instant("17:40:00"), key(r326, "1:2") to instant("17:48:45"))

            val pullIn = poll(at("17:48:45"), start, listOf(parked("1:2")))
            pullIn.assertHeadline(firstSeen, "pull in")
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), pullIn.rowAt(at("17:49:00"))?.state)
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("18:07:00")), pullIn.rowAt(at("18:07:00"))?.state)
            assertEquals(instant("17:48:30"), pullIn.memory.vehicles.getValue(key(r326, "1:1")).lastSeen)
            assertEquals(instant("17:48:45"), pullIn.memory.vehicles.getValue(key(r326, "1:2")).lastSeen)

            // Its row time passes while it is still left out: late, and the newcomer does not take the row.
            val late = poll(at("17:49:15"), pullIn.memory, listOf(parked("1:2")))
            late.assertHeadline(firstSeen, "late while held")
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("17:50:15"), Duration.ofSeconds(75)),
                late.rowAt(at("17:49:00"))?.state
            )
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("18:07:00")), late.rowAt(at("18:07:00"))?.state)

            // Back in the feed: the same late departure, lastSeen refreshed.
            val back = poll(at("17:49:30"), late.memory, listOf(parked("1:2"), parked("1:1")))
            back.assertHeadline(firstSeen, "back")
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("17:50:30"), Duration.ofSeconds(90)),
                back.rowAt(at("17:49:00"))?.state
            )
            assertEquals(instant("17:49:30"), back.memory.vehicles.getValue(key(r326, "1:1")).lastSeen)
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("18:07:00")), back.rowAt(at("18:07:00"))?.state)
        }

        @Test
        fun `when the hold runs out the newcomer moves up, with its own turnaround`() {
            val start = memory(key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            val firstSeen = mapOf(key(r326, "1:1") to instant("17:40:00"), key(r326, "1:2") to instant("17:45:15"))

            val held = poll(at("17:45:15"), start, listOf(parked("1:2")))
            held.assertHeadline(firstSeen, "held")
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("18:07:00")), held.rowAt(at("18:07:00"))?.state)

            val edge = poll(at("17:46:00"), held.memory, listOf(parked("1:2")))
            edge.assertHeadline(firstSeen, "edge")
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), edge.rowAt(at("17:49:00"))?.state)

            val gone = poll(at("17:46:15"), edge.memory, listOf(parked("1:2")))
            gone.assertHeadline(firstSeen, "gone")
            assertFalse(key(r326, "1:1") in gone.memory.vehicles)
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("17:49:00")), gone.rowAt(at("17:49:00"))?.state)
            assertEquals(1, gone.rowsOf("1:2").size)
        }

        @Test
        fun `a relabelled newcomer with an earlier first sighting still queues behind the held bus`() {
            // 1:2 was seen once, before 1:1, but got no row; 1:1 holds 17:49 and keeps it.
            val start = memory(
                key(r326, "1:1") to entry("17:41:00", "17:49:00", lastSeen = "17:45:00"),
                key(r326, "1:2") to entry("17:40:00", null, lastSeen = "17:45:00")
            )
            val result = poll(at("17:45:15"), start, listOf(parked("1:2", "1:01")))
            result.assertHeadline(
                mapOf(key(r326, "1:1") to instant("17:41:00"), key(r326, "1:2") to instant("17:40:00")),
                "earlier stamp"
            )
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), result.rowAt(at("17:49:00"))?.state)
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("18:07:00")), result.rowAt(at("18:07:00"))?.state)
        }
    }

    @Nested
    inner class RowTimePassesDuringTheDropout {
        @Test
        fun `waiting, then late on the right poll, then gone one window after it was last seen`() {
            // First seen 17:48:00, so its turnaround ends 17:50:00, after the 17:49 row.
            var mem = memory(key(r326, "1:1") to entry("17:48:00", "17:49:00", lastSeen = "17:48:30"))
            val firstSeen = mapOf(key(r326, "1:1") to instant("17:48:00"))
            val expected = listOf(
                "17:48:45" to DepartureState.Waiting(VehicleId("1:1"), at("17:50:00")),
                // At the row time exactly the row is not past yet, so still Waiting.
                "17:49:00" to DepartureState.Waiting(VehicleId("1:1"), at("17:50:00")),
                "17:49:15" to DepartureState.Late(VehicleId("1:1"), at("17:50:15"), Duration.ofSeconds(75)),
                "17:49:30" to DepartureState.Late(VehicleId("1:1"), at("17:50:30"), Duration.ofSeconds(90))
            )
            expected.forEach { (time, state) ->
                val result = poll(at(time), mem, emptyList())
                result.assertHeadline(firstSeen, time)
                assertEquals(state, result.rowAt(at("17:49:00"))?.state, time)
                val kept = result.memory.vehicles.getValue(key(r326, "1:1"))
                assertEquals(instant("17:48:30"), kept.lastSeen, "an unseen poll is not a sighting, $time")
                assertEquals(at("17:49:00"), kept.waitingFor, time)
                assertEquals(instant("17:48:00"), kept.firstSeen, time)
                mem = result.memory
            }
            val gone = poll(at("17:49:45"), mem, emptyList())
            assertEquals(LayoverMemory.Empty, gone.memory)
            assertEquals(DepartureState.TimetableOnly, gone.rowAt(at("17:49:00"))?.state)
            assertTrue(gone.rowsOf("1:1").isEmpty())
        }

        @Test
        fun `a held bus waiting for the last row of the day turns late and keeps no other row`() {
            val start = memory(key(r326, "1:1") to entry("18:03:00", "18:07:00", lastSeen = "18:07:00"))
            val result = poll(at("18:07:30"), start, emptyList())
            result.assertHeadline(mapOf(key(r326, "1:1") to instant("18:03:00")), "last row")
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("18:08:30"), Duration.ofSeconds(90)),
                result.rowAt(at("18:07:00"))?.state
            )
            assertEquals(1, result.rowsOf("1:1").size)
        }

        @Test
        fun `a held late bus is still dropped when its missed row passes the lookback`() {
            // Seen 17:59:30, late for 17:49; at 18:00 the trip is 11 whole minutes gone.
            val start = memory(key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:59:30"))
            val inside = poll(at("17:59:59"), start, emptyList())
            assertTrue(inside.rowAt(at("17:49:00"))?.state is DepartureState.Late)
            val past = poll(at("18:00:00"), inside.memory, emptyList())
            assertEquals(LayoverMemory.Empty, past.memory)
            assertTrue(past.rowsOf("1:1").isEmpty(), "${past.departures}")
            assertEquals(DepartureState.NoBusYet, past.rowAt(at("18:07:00"))?.state)
        }
    }

    @Nested
    inner class OfflineRouteInTheSamePoll {
        private val both = memory(
            key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"),
            key(r551, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:45:00")
        )
        private val firstSeen = mapOf(key(r326, "1:1") to instant("17:40:00"), key(r551, "1:1") to instant("17:40:00"))

        @Test
        fun `an offline route holds no row and keeps its entry, while a live route holds its own bus`() {
            val now = at("17:45:15")
            val result = predict(now, both, snapshot(r326, now, null), snapshot(r551, now, emptyList()))
            result.assertHeadline(firstSeen, "326 offline")
            assertTrue(
                result.departures.filter { it.routeId == r326.id }.all { it.state == DepartureState.TimetableOnly },
                "${result.departures}"
            )
            assertEquals(
                DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")),
                result.rowAt(at("17:49:00"), r551.id)?.state
            )
            assertEquals(both, result.memory, "neither the offline nor the held entry is refreshed")
        }

        @Test
        fun `a stale snapshot counts as offline even when it still lists the bus`() {
            val now = at("17:45:45")
            val stale = snapshot(r551, now, listOf(parked("1:1")), fetchedAt = instant("17:43:44"))
            val result = predict(now, both, snapshot(r326, now, emptyList()), stale)
            result.assertHeadline(firstSeen, "551 stale")
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), result.rowAt(at("17:49:00"))?.state)
            assertTrue(
                result.departures.filter {
                    it.routeId == r551.id
                }.all { it.state == DepartureState.TimetableOnly }
            )
            assertEquals(both, result.memory)
        }

        @Test
        fun `offline polls do not extend the window, so the first live poll after it holds nothing`() {
            var mem = both
            listOf("17:45:15", "17:45:30", "17:45:45", "17:46:00").forEach { time ->
                val now = at(time)
                mem = predict(now, mem, snapshot(r326, now, null), snapshot(r551, now, null)).memory
            }
            assertEquals(both, mem)
            val now = at("17:46:01")
            val live = predict(now, mem, snapshot(r326, now, emptyList()), snapshot(r551, now, emptyList()))
            assertEquals(LayoverMemory.Empty, live.memory)
            assertTrue(live.departures.none { it.state.vehicle() != null }, "${live.departures}")
            assertEquals(DepartureState.NoBusYet, live.rowAt(at("17:49:00"))?.state)
            assertEquals(DepartureState.NoBusYet, live.rowAt(at("17:49:00"), r551.id)?.state)
        }

        @Test
        fun `a late held bus and an offline route with the same late id in one poll`() {
            val late = memory(
                key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:50:00"),
                key(r551, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:50:00")
            )
            val now = at("17:50:30")
            val result = predict(now, late, snapshot(r326, now, emptyList()), snapshot(r551, now, null))
            result.assertHeadline(firstSeen, "late held, 551 offline")
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("17:51:30"), Duration.ofSeconds(150)),
                result.rowAt(at("17:49:00"))?.state
            )
            // Offline, 551's 17:49 is a plain timetable row, already past its grace and gone.
            assertNull(result.rowAt(at("17:49:00"), r551.id))
            assertTrue(
                result.departures.filter {
                    it.routeId == r551.id
                }.all { it.state == DepartureState.TimetableOnly }
            )
            assertEquals(late, result.memory)
        }
    }

    @Nested
    inner class ReusedIdOnAnotherRoute {
        private val held = memory(key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
        private val now = at("17:45:15")

        private fun withOn551(vehicle: VehiclePosition): StopPrediction =
            predict(now, held, snapshot(r326, now, emptyList()), snapshot(r551, now, listOf(vehicle)))

        @Test
        fun `the id driving on another route does not end the hold here`() {
            val result = withOn551(moving("1:1"))
            result.assertHeadline(mapOf(key(r326, "1:1") to instant("17:40:00")), "moving on 551")
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), result.rowAt(at("17:49:00"))?.state)
            assertTrue(result.rowsOf("1:1", r551.id).isEmpty())
            assertEquals(DepartureState.NoBusYet, result.rowAt(at("17:49:00"), r551.id)?.state)
            assertEquals(held, result.memory, "551 gets no entry for a moving bus")
        }

        @Test
        fun `the id parked far away on another route does not end the hold here`() {
            val result = withOn551(parked("1:1", at = LatLon(HOME.lat + 0.0054, HOME.lon)))
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), result.rowAt(at("17:49:00"))?.state)
            assertTrue(result.rowsOf("1:1", r551.id).isEmpty())
            assertEquals(held, result.memory)
        }

        @Test
        fun `the id parked here on another route is a new sighting there and held here`() {
            val result = withOn551(parked("1:1"))
            result.assertHeadline(
                mapOf(key(r326, "1:1") to instant("17:40:00"), key(r551, "1:1") to now.toInstant()),
                "parked on both"
            )
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), result.rowAt(at("17:49:00"))?.state)
            assertEquals(
                DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")),
                result.rowAt(at("17:49:00"), r551.id)?.state
            )
            val on551 = result.memory.vehicles.getValue(key(r551, "1:1"))
            assertEquals(now.toInstant(), on551.firstSeen)
            assertEquals(now.toInstant(), on551.lastSeen)
            assertEquals(held.vehicles.getValue(key(r326, "1:1")), result.memory.vehicles.getValue(key(r326, "1:1")))
        }

        @Test
        fun `an id held on another route does not hold a row on a route that never saw it`() {
            val on551 = memory(key(r551, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            val result = predict(now, on551, snapshot(r326, now, emptyList()), snapshot(r551, now, emptyList()))
            assertTrue(result.rowsOf("1:1").isEmpty())
            assertEquals(DepartureState.NoBusYet, result.rowAt(at("17:49:00"))?.state)
            assertEquals(
                DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")),
                result.rowAt(at("17:49:00"), r551.id)?.state
            )
        }
    }

    @Nested
    inner class AcrossMidnight {
        // Sunday 2026-09-27; its 24:00 and 24:30 are Monday 00:00 and 00:30.
        private val sunday = "2026-09-27"
        private val monday = "2026-09-28"
        private val lateEvening = listOf("23:30", "24:00", "24:30")
        private val midnightRow get() = at("${monday}T00:00:00")

        @Test
        fun `held from before midnight, it turns late for the 24 00 row after midnight and then goes`() {
            val first = poll(at("${sunday}T23:59:30"), LayoverMemory.Empty, listOf(parked("1:1")), lateEvening)
            val firstSeen = mapOf(key(r326, "1:1") to instant("${sunday}T23:59:30"))
            first.assertHeadline(firstSeen, "seen")

            val waiting = poll(at("${sunday}T23:59:45"), first.memory, emptyList(), lateEvening)
            waiting.assertHeadline(firstSeen, "held before midnight")
            assertEquals(
                DepartureState.Waiting(VehicleId("1:1"), at("${monday}T00:01:30")),
                waiting.rowAt(midnightRow)?.state
            )

            val late = poll(at("${monday}T00:00:15"), waiting.memory, emptyList(), lateEvening)
            late.assertHeadline(firstSeen, "held after midnight")
            val row = checkNotNull(late.rowAt(midnightRow))
            // Its turnaround still ends later than one step after now.
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("${monday}T00:01:30"), Duration.ofSeconds(90)),
                row.state
            )
            assertEquals(LocalDate.parse(sunday), row.serviceDate)
            assertEquals(instant("${sunday}T23:59:30"), late.memory.vehicles.getValue(key(r326, "1:1")).lastSeen)

            val edge = poll(at("${monday}T00:00:30"), late.memory, emptyList(), lateEvening)
            edge.assertHeadline(firstSeen, "edge")
            assertTrue(edge.rowAt(midnightRow)?.state is DepartureState.Late)

            val gone = poll(at("${monday}T00:00:31"), edge.memory, emptyList(), lateEvening)
            assertEquals(LayoverMemory.Empty, gone.memory)
            assertEquals(DepartureState.TimetableOnly, gone.rowAt(midnightRow)?.state)
        }

        @Test
        fun `a bus held over midnight and back after it keeps its evening sighting`() {
            val start = memory(
                key(r326, "1:1") to entry("${sunday}T23:55:00", "${monday}T00:00:00", lastSeen = "${sunday}T23:59:40")
            )
            val firstSeen = mapOf(key(r326, "1:1") to instant("${sunday}T23:55:00"))
            val held = poll(at("${monday}T00:00:10"), start, emptyList(), lateEvening)
            held.assertHeadline(firstSeen, "held")
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("${monday}T00:01:10"), Duration.ofSeconds(70)),
                held.rowAt(midnightRow)?.state
            )
            val back = poll(at("${monday}T00:00:25"), held.memory, listOf(parked("1:1")), lateEvening)
            back.assertHeadline(firstSeen, "back")
            assertEquals(instant("${sunday}T23:55:00"), back.memory.vehicles.getValue(key(r326, "1:1")).firstSeen)
            assertEquals(instant("${monday}T00:00:25"), back.memory.vehicles.getValue(key(r326, "1:1")).lastSeen)
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("${monday}T00:01:25"), Duration.ofSeconds(85)),
                back.rowAt(midnightRow)?.state
            )
        }
    }

    /**
     * The point of the hold: a feed that leaves parked buses out for up to 45 s at a time
     * must show exactly what a feed that always lists them shows, poll by poll.
     */
    @Nested
    inner class NoFlicker {
        @Test
        fun `dropouts of up to three polls change nothing on screen over 40 seeded sessions`() {
            var heldPolls = 0
            repeat(SESSIONS) { seed ->
                heldPolls += runSession(Random(SEED + seed), seed)
            }
            assertTrue(heldPolls >= 400, "too few polls with a bus left out: $heldPolls")
        }

        /** Returns how many polls left at least one parked bus out. */
        private fun runSession(random: Random, seed: Int): Int {
            // 1:1 parks at 17:44 for the 17:49 row and stays late until the trip is dropped at 18:00.
            val start = at("17:44:00")
            val arrival2 = start.plusSeconds(POLL_SECONDS * random.nextInt(1, 30))
            // Consecutive polls a bus has been left out, and how long its current gap lasts.
            val missed = mutableMapOf("1:1" to 0, "1:2" to 0)
            val gap = mutableMapOf("1:1" to 0, "1:2" to 0)
            var truth = LayoverMemory.Empty
            var flicker = LayoverMemory.Empty
            var held = 0
            var now = start
            while (now.isBefore(at("17:59:45"))) {
                val present = buildList {
                    add("1:1")
                    if (!now.isBefore(arrival2)) add("1:2")
                }
                val listed = present.filter { bus ->
                    val firstPoll = (bus == "1:1" && now == start) || (bus == "1:2" && now == arrival2)
                    val inGap = missed.getValue(bus) in 1 until gap.getValue(bus)
                    val startsGap = !firstPoll && missed.getValue(bus) == 0 && random.nextInt(3) == 0
                    if (startsGap) gap[bus] = random.nextInt(1, MAX_GAP_POLLS + 1)
                    val left = inGap || startsGap
                    missed[bus] = if (left) missed.getValue(bus) + 1 else 0
                    !left
                }
                if (listed.size < present.size) held++
                val seen = poll(now, truth, present.map { parked(it) })
                val gappy = poll(now, flicker, listed.map { parked(it) })
                val context = "seed $seed at $now, listed $listed of $present"
                assertEquals(seen.departures, gappy.departures, context)
                gappy.assertHeadline(
                    buildMap {
                        put(key(r326, "1:1"), start.toInstant())
                        put(key(r326, "1:2"), arrival2.toInstant())
                    },
                    context
                )
                // Same queue state; only lastSeen may lag.
                assertEquals(
                    seen.memory.vehicles.mapValues { (_, v) -> v.copy(lastSeen = Instant.EPOCH) },
                    gappy.memory.vehicles.mapValues { (_, v) -> v.copy(lastSeen = Instant.EPOCH) },
                    context
                )
                truth = seen.memory
                flicker = gappy.memory
                now = now.plusSeconds(POLL_SECONDS)
            }
            return held
        }
    }

    @Test
    fun `a held bus is never shown twice, even when the feed lists it under another pattern next poll`() {
        val start = memory(key(r326, "1:1") to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
        val gap = poll(at("17:45:15"), start, emptyList())
        val relabelled = poll(at("17:45:30"), gap.memory, listOf(parked("1:1", "1:01")))
        relabelled.assertHeadline(mapOf(key(r326, "1:1") to instant("17:40:00")), "relabelled")
        assertEquals(1, relabelled.rowsOf("1:1").size)
        assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), relabelled.rowAt(at("17:49:00"))?.state)
        assertNull(relabelled.rowAt(at("17:40:00"), pattern = PatternSuffix("1:01"))?.state?.vehicle())
    }

    private companion object {
        val TURNAROUND: Duration = PredictionRules.Default.minTurnaround
        const val POLL_SECONDS = 15L
        const val SESSIONS = 40
        const val SEED = 20260930
        const val MAX_GAP_POLLS = 3
    }
}
