package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.predict.testing.Synthetic.HOME
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.positions
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.clock
import ge.hackerman.gza.core.predict.testing.memoryOf
import ge.hackerman.gza.core.predict.testing.of
import ge.hackerman.gza.core.predict.testing.remembers
import ge.hackerman.gza.core.predict.testing.tbilisi
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The layover memory rules at their exact edges (dropout, max age, clock skew, late lookback)
 * and over sequences of polls: flickering buses, reused vehicle ids, relabelling and a bus
 * parked across midnight. Every sequence also checks the headline rule: a parked bus never
 * leaves before max(its timetable time, its first sighting + 2 min).
 */
class LayoverMemoryRulesAdversarialTest {
    private val r326 = route("326")
    private val r551 = route("551", id = "1:minibusR24579")
    private val out = PatternSuffix("0:01")
    private val day = "2026-09-28"
    private val times = listOf("17:31", "17:49", "18:07")

    private fun at(time: String): ZonedDateTime = if ('T' in time) tbilisi(time) else tbilisi("${day}T$time")

    private fun instant(time: String): Instant = at(time).toInstant()

    /** 326 `0:01` starts at home; `1:01` passes it at position 2 at other times. */
    private fun schedules(route: Route, outbound: List<String>): List<RouteSchedule> = listOf(
        schedule(route, "0:01", outbound),
        schedule(route, "1:01", listOf("17:40", "18:20"), position = 2)
    )

    private fun snapshot(
        route: Route,
        now: ZonedDateTime,
        vehicles: List<VehiclePosition>?,
        outbound: List<String> = times,
        fetchedAt: Instant = now.toInstant()
    ) = RouteSnapshot(
        route,
        schedules(route, outbound),
        positions = vehicles?.let {
            positions(route, fetchedAt, *it.toTypedArray())
        }
    )

    private fun poll(
        now: ZonedDateTime,
        memory: LayoverMemory,
        vehicles: List<VehiclePosition>?,
        outbound: List<String> = times,
        fetchedAt: Instant = now.toInstant(),
        others: List<RouteSnapshot> = emptyList()
    ): StopPrediction = DeparturePredictor(now.toInstant().clock()).predict(
        StopRequest(home, listOf(snapshot(r326, now, vehicles, outbound, fetchedAt)) + others, memory = memory)
    )

    private fun entry(
        firstSeen: String,
        waitingFor: String?,
        lastSeen: String = firstSeen,
        pattern: PatternSuffix = out
    ) = ParkedVehicle(pattern, instant(firstSeen), waitingFor?.let(::at), instant(lastSeen))

    private fun StopPrediction.rowOf(vehicle: String): PredictedDeparture? = departures.singleOrNull {
        when (val state = it.state) {
            is DepartureState.Waiting -> state.vehicleId.value == vehicle
            is DepartureState.Late -> state.vehicleId.value == vehicle
            else -> false
        }
    }

    private fun StopPrediction.rowAt(time: ZonedDateTime, pattern: PatternSuffix = out): PredictedDeparture? =
        departures.singleOrNull { it.pattern == pattern && it.scheduled.isEqual(time) && it.routeId == r326.id }

    /**
     * The headline rule on one poll: every bus shown is on one row, never leaves before its
     * timetable time (Waiting), never before now, and never before [firstSeen] + 2 min.
     */
    private fun StopPrediction.assertHeadline(firstSeen: Map<String, Instant>, context: String) {
        val shown = departures.mapNotNull { row ->
            val (vehicle, leavesAt) = when (val state = row.state) {
                is DepartureState.Waiting -> {
                    assertFalse(state.leavesAt.isBefore(row.scheduled), "left before its timetable, $context: $row")
                    state.vehicleId.value to state.leavesAt
                }

                is DepartureState.Late -> {
                    assertTrue(state.leavesAt.isAfter(now), "late but leaving now, $context: $row")
                    state.vehicleId.value to state.leavesAt
                }

                else -> return@mapNotNull null
            }
            assertFalse(leavesAt.isBefore(now), "arriving now, $context: $row")
            firstSeen[vehicle]?.let {
                assertFalse(leavesAt.toInstant().isBefore(it.plus(TURNAROUND)), "turnaround waived, $context: $row")
            }
            row.routeId to vehicle
        }
        assertEquals(shown.distinct(), shown, "one row per bus, $context")
    }

    @Nested
    inner class Boundaries {
        @Test
        fun `an unseen entry survives exactly 1 min after it was last seen and not 1 ns more`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            assertEquals(memory, poll(at("17:46:00"), memory, emptyList()).memory)
            assertEquals(LayoverMemory.Empty, poll(at("17:46:00").plusNanos(1), memory, emptyList()).memory)
        }

        @Test
        fun `several dropout polls in a row do not stretch the window`() {
            var memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            listOf("17:45:15", "17:45:30", "17:45:45", "17:46:00").forEach { time ->
                memory = poll(at(time), memory, emptyList()).memory
                assertEquals(instant("17:45:00"), memory.of("1:1").lastSeen, "an unseen poll is not a sighting, $time")
            }
            assertEquals(LayoverMemory.Empty, poll(at("17:46:00").plusNanos(1), memory, emptyList()).memory)
        }

        @Test
        fun `a late bus back after a dropout of exactly 1 min is still late`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:50:00"))
            val gap = poll(at("17:51:00"), memory, emptyList()).memory
            val back = poll(at("17:51:10"), gap, listOf(parked("1:1")))
            val row = checkNotNull(back.rowOf("1:1"))
            assertEquals(DepartureState.Late(VehicleId("1:1"), at("17:52:10"), Duration.ofSeconds(190)), row.state)
            assertEquals(instant("17:40:00"), back.memory.of("1:1").firstSeen)
        }

        @Test
        fun `a bus back after a dropout 1 ns too long is a new sighting and not late`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:50:00"))
            val gap = poll(at("17:51:00").plusNanos(1), memory, emptyList()).memory
            val back = poll(at("17:51:10"), gap, listOf(parked("1:1")))
            assertTrue(back.departures.none { it.state is DepartureState.Late })
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("18:07:00")), back.rowOf("1:1")?.state)
            assertEquals(instant("17:51:10"), back.memory.of("1:1").firstSeen)
        }

        @Test
        fun `an entry last seen exactly 30 min ago keeps its first sighting and not 1 ns more`() {
            // No departure to wait for, so only the age decides.
            val memory = memoryOf(r326.id, "1:1" to entry("17:00:00", null, lastSeen = "17:10:00"))
            val outbound = listOf("17:41", "18:07")

            val exact = poll(at("17:40:00"), memory, listOf(parked("1:1")), outbound)
            assertEquals(instant("17:00:00"), exact.memory.of("1:1").firstSeen)
            assertEquals(at("17:41:00"), exact.rowOf("1:1")?.predicted)

            val justOver = at("17:40:00").plusNanos(1)
            val stale = poll(justOver, memory, listOf(parked("1:1")), outbound)
            assertEquals(justOver.toInstant(), stale.memory.of("1:1").firstSeen)
            assertEquals(justOver.plus(TURNAROUND), stale.rowOf("1:1")?.predicted)
        }

        @Test
        fun `a stale entry kept by offline polls is forgotten by the first live poll`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:00:00", null, lastSeen = "17:10:00"))
            val offline = poll(at("17:45:00"), memory, vehicles = null)
            assertEquals(memory, offline.memory, "an offline poll changes nothing")
            assertEquals(LayoverMemory.Empty, poll(at("17:45:15"), offline.memory, emptyList()).memory)
        }

        @Test
        fun `an entry waiting for a departure 10 whole minutes gone survives a dropout, 11 does not`() {
            val tenWhole = memoryOf(r326.id, "1:1" to entry("17:30:00", "17:49:00", lastSeen = "17:59:50"))
            assertTrue(poll(at("17:59:59.999"), tenWhole, emptyList()).memory.remembers(VehicleId("1:1")))
            assertEquals(LayoverMemory.Empty, poll(at("18:00:00"), tenWhole, emptyList()).memory)
        }

        @Test
        fun `positions stamped exactly 2 min ahead are live and 1 ns more are offline`() {
            // 1:9 is past its dropout, so a live poll forgets it and does not hold a row for it.
            val memory = memoryOf(r326.id, "1:9" to entry("17:40:00", null, lastSeen = "17:43:00"))
            val now = at("17:45:00")
            val bus = listOf(parked("1:1"))

            val live = poll(now, memory, bus, fetchedAt = now.plusMinutes(2).toInstant())
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), live.rowOf("1:1")?.state)
            assertEquals(now.toInstant(), live.memory.of("1:1").firstSeen, "first seen now, not at the stamp")
            assertEquals(now.toInstant(), live.memory.of("1:1").lastSeen)

            val ahead = poll(now, memory, bus, fetchedAt = now.plusMinutes(2).plusNanos(1).toInstant())
            assertOffline(ahead, memory)
        }

        @Test
        fun `positions exactly 2 min old are live and 1 ns older are offline`() {
            // 1:9 is past its dropout, so only 1:1 can take the row.
            val memory = memoryOf(r326.id, "1:9" to entry("17:40:00", null, lastSeen = "17:43:00"))
            val now = at("17:45:00")
            val bus = listOf(parked("1:1"))

            val live = poll(now, memory, bus, fetchedAt = now.minusMinutes(2).toInstant())
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), live.rowOf("1:1")?.state)
            assertEquals(now.toInstant(), live.memory.of("1:1").firstSeen)
            assertFalse(live.memory.remembers(VehicleId("1:9")))

            val old = poll(now, memory, bus, fetchedAt = now.minusMinutes(2).minusNanos(1).toInstant())
            assertOffline(old, memory)
        }

        @Test
        fun `a skewed offline poll does not refresh last seen, so a later dropout still expires`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            // The bus is in the skewed snapshot, but the snapshot is not trusted.
            val skewed = poll(at("17:45:40"), memory, listOf(parked("1:1")), fetchedAt = instant("17:48:00"))
            assertEquals(memory, skewed.memory)
            assertEquals(LayoverMemory.Empty, poll(at("17:46:01"), skewed.memory, emptyList()).memory)
        }

        private fun assertOffline(result: StopPrediction, memory: LayoverMemory) {
            assertTrue(
                result.departures.all {
                    it.state == DepartureState.TimetableOnly
                },
                "offline: ${result.departures}"
            )
            assertEquals(memory, result.memory, "an offline poll keeps the memory as it was")
        }
    }

    @Nested
    inner class Flicker {
        @Test
        fun `a bus seen on every other poll for 25 min keeps its sighting, waits, turns late, then queues`() {
            var memory = LayoverMemory.Empty
            var now = at("17:40:00")
            var poll = 0
            while (!now.isAfter(at("18:05:00"))) {
                val present = poll % 2 == 0
                val result = poll(now, memory, if (present) listOf(parked("1:1")) else emptyList())
                val context = "poll $poll at ${now.toLocalTime()}"
                assertTrue(result.memory.remembers(VehicleId("1:1")), "remembered through the flicker, $context")
                if (present) {
                    val firstSeen = if (now.isBefore(at("18:00:00"))) instant("17:40:00") else instant("18:00:00")
                    assertEquals(firstSeen, result.memory.of("1:1").firstSeen, context)
                    result.assertHeadline(mapOf("1:1" to firstSeen), context)
                    val expected = when {
                        !now.isAfter(at("17:49:00")) -> DepartureState.Waiting(VehicleId("1:1"), at("17:49:00"))

                        now.isBefore(at("18:00:00")) ->
                            DepartureState.Late(
                                VehicleId("1:1"),
                                now.plusMinutes(1),
                                Duration.between(at("17:49:00"), now.plusMinutes(1))
                            )

                        else -> DepartureState.Waiting(VehicleId("1:1"), at("18:07:00"))
                    }
                    assertEquals(expected, result.rowOf("1:1")?.state, context)
                }
                memory = result.memory
                now = now.plusSeconds(POLL_SECONDS)
                poll++
            }
        }

        @Test
        fun `gaps of up to 1 min keep the sighting, a 75 s gap starts a new one`() {
            val outbound = listOf("17:43", "18:07")
            val script = listOf(
                "17:40:00" to true, "17:40:15" to false, "17:40:30" to false, "17:40:45" to false,
                "17:41:00" to false, "17:41:15" to true, "17:41:30" to false, "17:41:45" to false,
                "17:42:00" to false, "17:42:15" to false, "17:42:30" to false, "17:42:45" to true
            )
            var memory = LayoverMemory.Empty
            val results = script.associate { (time, present) ->
                val result = poll(at(time), memory, if (present) listOf(parked("1:1")) else emptyList(), outbound)
                memory = result.memory
                time to result
            }
            assertEquals(at("17:43:00"), results.getValue("17:41:15").rowOf("1:1")?.predicted)
            assertEquals(instant("17:40:00"), results.getValue("17:41:15").memory.of("1:1").firstSeen)
            assertTrue(results.getValue("17:42:15").memory.remembers(VehicleId("1:1")), "60 s after 17:41:15")
            assertEquals(LayoverMemory.Empty, results.getValue("17:42:30").memory, "75 s after 17:41:15")
            val back = results.getValue("17:42:45")
            assertEquals(instant("17:42:45"), back.memory.of("1:1").firstSeen)
            assertEquals(at("17:44:45"), back.rowOf("1:1")?.predicted, "a new turnaround")
        }

        @Test
        fun `offline polls between sightings keep the first sighting for up to 30 min`() {
            val outbound = listOf("18:30")
            var memory = poll(at("17:40:00"), LayoverMemory.Empty, listOf(parked("1:1")), outbound).memory
            memory = poll(at("17:40:15"), memory, vehicles = null, outbound).memory
            memory =
                poll(at("17:44:00"), memory, listOf(parked("1:1")), outbound, fetchedAt = instant("17:41:00")).memory
            val back = poll(at("17:45:15"), memory, listOf(parked("1:1")), outbound)
            assertEquals(instant("17:40:00"), back.memory.of("1:1").firstSeen)

            val offline = poll(at("17:50:00"), back.memory, vehicles = null, outbound).memory
            val within = poll(at("18:15:15"), offline, listOf(parked("1:1")), outbound)
            assertEquals(instant("17:40:00"), within.memory.of("1:1").firstSeen, "exactly 30 min after 17:45:15")
            val beyond = poll(at("18:15:15").plusNanos(1), offline, listOf(parked("1:1")), outbound)
            assertEquals(at("18:15:15").plusNanos(1).toInstant(), beyond.memory.of("1:1").firstSeen)
        }

        @Test
        fun `a bus that flickers while relabelled on each return stays on its departure`() {
            val script = listOf(
                Triple("17:48:00", true, "0:01"),
                Triple("17:48:15", false, ""),
                Triple("17:48:30", true, "1:01"),
                Triple("17:48:45", false, ""),
                Triple("17:49:30", true, "1:01"),
                Triple("17:49:45", true, "0:01"),
                Triple("17:50:00", false, ""),
                Triple("17:50:15", true, "1:01")
            )
            var memory = memoryOf(r326.id, "1:1" to entry("17:30:00", null, lastSeen = "17:47:45"))
            script.forEach { (time, present, label) ->
                val result = poll(at(time), memory, if (present) listOf(parked("1:1", label)) else emptyList())
                memory = result.memory
                assertEquals(out, memory.of("1:1").pattern, "counted for the pattern leaving here, $time")
                assertEquals(instant("17:30:00"), memory.of("1:1").firstSeen, time)
                if (present) {
                    result.assertHeadline(mapOf("1:1" to instant("17:30:00")), time)
                    val row = checkNotNull(result.rowOf("1:1")) { time }
                    assertTrue(row.scheduled.isEqual(at("17:49:00")), "stays on 17:49, $time: $row")
                    assertEquals(
                        if (at(time).isBefore(at("17:49:00"))) at("17:49:00") else at(time).plusMinutes(1),
                        row.predicted,
                        time
                    )
                }
            }
        }
    }

    @Nested
    inner class ReusedVehicleId {
        @Test
        fun `late on 326 does not make the same id on 551 late`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:30:00", "17:49:00", lastSeen = "17:49:45"))
            val now = at("17:50:00")
            val on551 = snapshot(r551, now, listOf(parked("1:1")))
            val result = poll(now, memory, emptyList(), others = listOf(on551))

            // 551 never saw it waiting, so no late there; and 1:1 is listed on live 551, so GPS
            // wins and 326 does not hold it through the dropout either.
            assertTrue(
                result.departures.none { it.state is DepartureState.Late },
                "${result.departures}"
            )
            assertTrue(result.departures.none { it.routeId == r326.id && it.state is DepartureState.Waiting })
            val row551 = result.departures.single { it.routeId == r551.id && it.state is DepartureState.Waiting }
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("18:07:00")), row551.state)
            assertEquals(
                now.toInstant(),
                result.memory.vehicles.getValue(ParkedKey(r551.id, VehicleId("1:1"))).firstSeen
            )
            // 326 still keeps its own entry through the dropout, unchanged.
            assertEquals(memory.of("1:1"), result.memory.vehicles.getValue(ParkedKey(r326.id, VehicleId("1:1"))))
        }

        @Test
        fun `the same id parked on two routes gets a row on each with its own turnaround`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:30:00", "17:49:00", lastSeen = "17:48:15"))
            val now = at("17:48:30")
            val result =
                poll(now, memory, listOf(parked("1:1")), others = listOf(snapshot(r551, now, listOf(parked("1:1")))))
            val rows = result.departures.filter { it.state is DepartureState.Waiting }
            assertEquals(at("17:49:00"), rows.single { it.routeId == r326.id }.predicted)
            assertEquals(at("17:50:30"), rows.single { it.routeId == r551.id }.predicted, "551 has never seen it")
            result.assertHeadline(emptyMap(), "two routes")
        }

        @Test
        fun `an id leaving one route while parked on the other only ends the first history`() {
            var memory = LayoverMemory.Empty
            val seen = listOf("17:40:00", "17:40:15")
            seen.forEach { time ->
                val now = at(time)
                memory =
                    poll(
                        now,
                        memory,
                        listOf(parked("1:1")),
                        others = listOf(snapshot(r551, now, listOf(parked("1:1"))))
                    ).memory
            }
            val later = at("17:41:30")
            val result = poll(later, memory, emptyList(), others = listOf(snapshot(r551, later, listOf(parked("1:1")))))
            assertEquals(setOf(ParkedKey(r551.id, VehicleId("1:1"))), result.memory.vehicles.keys)
            assertEquals(
                instant("17:40:00"),
                result.memory.vehicles.getValue(ParkedKey(r551.id, VehicleId("1:1"))).firstSeen
            )

            // Back on 326 it is a new sighting there, while 551 keeps its old one.
            val back = at("17:41:45")
            val again =
                poll(
                    back,
                    result.memory,
                    listOf(parked("1:1")),
                    others = listOf(snapshot(r551, back, listOf(parked("1:1"))))
                )
            assertEquals(
                back.toInstant(),
                again.memory.vehicles.getValue(ParkedKey(r326.id, VehicleId("1:1"))).firstSeen
            )
            assertEquals(
                instant("17:40:00"),
                again.memory.vehicles.getValue(ParkedKey(r551.id, VehicleId("1:1"))).firstSeen
            )
        }
    }

    @Nested
    inner class Relabelling {
        @Test
        fun `a late bus relabelled to the pattern that ends here stays late`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:49:45"))
            val result = poll(at("17:50:00"), memory, listOf(parked("1:1", "1:01")))
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("17:51:00"), Duration.ofMinutes(2)),
                result.rowAt(at("17:49:00"))?.state
            )
            assertEquals(out, result.memory.of("1:1").pattern)
        }

        @Test
        fun `a relabelled bus back from a dropout keeps its pattern, first sighting and departure`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:30:00", "17:49:00", lastSeen = "17:45:00"))
            val gap = poll(at("17:45:30"), memory, emptyList()).memory
            val back = poll(at("17:45:45"), gap, listOf(parked("1:1", "1:01")))
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), back.rowAt(at("17:49:00"))?.state)
            assertEquals(entry("17:30:00", "17:49:00", lastSeen = "17:45:45"), back.memory.of("1:1"))
        }

        @Test
        fun `relabelled between two patterns that both start here it keeps its first sighting`() {
            val second = PatternSuffix("0:02")
            val snapshot = { now: ZonedDateTime, label: String ->
                RouteSnapshot(
                    r326,
                    listOf(
                        schedule(r326, "0:01", listOf("17:49", "18:07")),
                        schedule(r326, "0:02", listOf("17:55", "18:20"))
                    ),
                    positions = positions(r326, now.toInstant(), parked("1:1", label))
                )
            }
            val predict = { now: ZonedDateTime, memory: LayoverMemory, label: String ->
                DeparturePredictor(
                    now.toInstant().clock()
                ).predict(StopRequest(home, listOf(snapshot(now, label)), memory = memory))
            }
            val first = predict(at("17:47:30"), LayoverMemory.Empty, "0:01")
            assertEquals(at("17:49:30"), first.rowOf("1:1")?.predicted)

            // The label wins when it names a pattern that starts here (plan 3.7).
            val relabelled = predict(at("17:48:00"), first.memory, "0:02")
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:55:00")), relabelled.rowOf("1:1")?.state)
            assertEquals(second, relabelled.memory.of("1:1").pattern)
            assertEquals(instant("17:47:30"), relabelled.memory.of("1:1").firstSeen)
            assertEquals(DepartureState.NoBusYet, relabelled.rowAt(at("17:49:00"))?.state)

            // And back again before 17:49: the turnaround still runs from 17:47:30.
            val back = predict(at("17:48:15"), relabelled.memory, "0:01")
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:30")), back.rowOf("1:1")?.state)
        }

        @Test
        fun `a bus listed as both patterns and then only as the inbound one is one bus throughout`() {
            val now = at("17:48:00")
            val twice = poll(now, LayoverMemory.Empty, listOf(parked("1:1", "1:01"), parked("1:1", "0:01")))
            assertEquals(1, twice.departures.count { it.state is DepartureState.Waiting })
            assertEquals(1, twice.memory.vehicles.size)

            val once = poll(at("17:48:15"), twice.memory, listOf(parked("1:1", "1:01")))
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:50:00")), once.rowOf("1:1")?.state)
            assertEquals(instant("17:48:00"), once.memory.of("1:1").firstSeen)
        }
    }

    @Nested
    inner class Midnight {
        // Sunday 2026-09-27; its 24:00 and 24:30 are Monday 00:00 and 00:30.
        private val sunday = "2026-09-27"
        private val monday = "2026-09-28"
        private val lateEvening = listOf("23:30", "24:00", "24:30")

        @Test
        fun `first seen at 23 59 30 for the 24 00 departure it leaves at 00 01 30, late across midnight`() {
            val first = poll(at("${sunday}T23:59:30"), LayoverMemory.Empty, listOf(parked("1:1")), lateEvening)
            val row = checkNotNull(first.rowOf("1:1"))
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("${monday}T00:01:30")), row.state)
            assertEquals(LocalDate.parse(sunday), row.serviceDate)

            val after = poll(at("${monday}T00:00:30"), first.memory, listOf(parked("1:1")), lateEvening)
            val late = checkNotNull(after.rowOf("1:1"))
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("${monday}T00:01:30"), Duration.ofSeconds(90)),
                late.state
            )
            assertEquals(LocalDate.parse(sunday), late.serviceDate)
        }

        @Test
        fun `a bus left out of the poll at midnight keeps its sighting from before midnight`() {
            var memory = poll(at("${sunday}T23:59:50"), LayoverMemory.Empty, listOf(parked("1:1")), lateEvening).memory
            memory = poll(at("${monday}T00:00:05"), memory, emptyList(), lateEvening).memory
            memory = poll(at("${monday}T00:00:35"), memory, emptyList(), lateEvening).memory
            val back = poll(at("${monday}T00:00:50"), memory, listOf(parked("1:1")), lateEvening)
            assertEquals(instant("${sunday}T23:59:50"), back.memory.of("1:1").firstSeen)
            assertEquals(at("${monday}T00:01:50"), back.rowOf("1:1")?.predicted)
            assertTrue(back.rowOf("1:1")?.state is DepartureState.Late)
        }

        @Test
        fun `parked from 23 50 to 00 20 it is never shown leaving early`() {
            val outbound = listOf("23:55", "24:05", "24:15")
            var memory = LayoverMemory.Empty
            var now = at("${sunday}T23:50:00")
            var firstSeen = now.toInstant()
            val states = mutableListOf<Pair<ZonedDateTime, DepartureState?>>()
            while (!now.isAfter(at("${monday}T00:20:00"))) {
                val result = poll(now, memory, listOf(parked("1:1")), outbound)
                // Late for 23:55 for 10 whole minutes; then that trip is dropped and the bus is a new sighting.
                if (now.isEqual(at("${monday}T00:06:00"))) firstSeen = now.toInstant()
                assertEquals(firstSeen, result.memory.of("1:1").firstSeen, "$now")
                result.assertHeadline(mapOf("1:1" to firstSeen), "$now")
                states += now to result.rowOf("1:1")?.state
                memory = result.memory
                now = now.plusSeconds(POLL_SECONDS)
            }
            val byTime = states.toMap()
            assertEquals(
                DepartureState.Waiting(VehicleId("1:1"), at("${sunday}T23:55:00")),
                byTime[at("${sunday}T23:52:00")]
            )
            assertTrue(byTime[at("${monday}T00:05:45")] is DepartureState.Late)
            assertEquals(
                DepartureState.Waiting(VehicleId("1:1"), at("${monday}T00:15:00")),
                byTime[at("${monday}T00:06:00")]
            )
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("${monday}T00:17:00"), Duration.ofMinutes(2)),
                byTime[at("${monday}T00:16:00")]
            )
        }
    }

    /**
     * A remembered bus left out of a poll within the dropout window still holds its row, with
     * the same leave-time rules as if it had been seen, so the list does not flicker.
     */
    @Nested
    inner class RowsDuringADropout {
        @Test
        fun `a waiting bus left out of one poll still holds its departure`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            val gap = poll(at("17:45:15"), memory, emptyList())
            assertTrue(gap.memory.remembers(VehicleId("1:1")))
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), gap.rowAt(at("17:49:00"))?.state)
        }

        @Test
        fun `a late bus left out of one poll keeps its late departure listed`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:50:00"))
            val gap = poll(at("17:50:15"), memory, emptyList())
            assertTrue(gap.memory.remembers(VehicleId("1:1")))
            assertTrue(gap.rowAt(at("17:49:00"))?.state is DepartureState.Late, "${gap.departures}")
        }

        @Test
        fun `what a dropout poll shows`() {
            // Same leave times as if seen, and lastSeen stays the poll that saw it.
            val waiting = memoryOf(r326.id, "1:1" to entry("17:48:30", "17:49:00", lastSeen = "17:48:45"))
            val gap = poll(at("17:49:00"), waiting, emptyList())
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:50:30")), gap.rowAt(at("17:49:00"))?.state)
            assertEquals(
                instant("17:48:45"),
                gap.memory.vehicles.getValue(ParkedKey(r326.id, VehicleId("1:1"))).lastSeen
            )
            gap.assertHeadline(mapOf("1:1" to instant("17:48:30")), "waiting dropout")

            val late = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:50:00"))
            val lateGap = poll(at("17:50:15"), late, emptyList())
            assertEquals(
                DepartureState.Late(VehicleId("1:1"), at("17:51:15"), Duration.parse("PT2M15S")),
                lateGap.rowAt(at("17:49:00"))?.state
            )
            assertEquals(
                instant("17:50:00"),
                lateGap.memory.vehicles.getValue(ParkedKey(r326.id, VehicleId("1:1"))).lastSeen
            )
            lateGap.assertHeadline(mapOf("1:1" to instant("17:40:00")), "late dropout")
        }

        @Test
        fun `a dropout does not stretch the window`() {
            // Polls without the bus keep lastSeen, so the entry and its row end one window after it was seen.
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            val inside = poll(at("17:45:45"), memory, emptyList())
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), inside.rowAt(at("17:49:00"))?.state)
            val atEdge = poll(at("17:46:00"), inside.memory, emptyList())
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), atEdge.rowAt(at("17:49:00"))?.state)
            val after = poll(at("17:46:01"), atEdge.memory, emptyList())
            assertFalse(after.memory.remembers(VehicleId("1:1")))
            assertEquals(DepartureState.NoBusYet, after.rowAt(at("17:49:00"))?.state)
        }

        @Test
        fun `a bus in the feed but no longer parked does not hold its row`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            val moving = VehiclePosition(VehicleId("1:1"), out, HOME, 90.0, StopId("1:969"))
            val gap = poll(at("17:45:15"), memory, listOf(moving))
            assertNull(gap.rowOf("1:1"))
            assertEquals(DepartureState.NoBusYet, gap.rowAt(at("17:49:00"))?.state)
        }

        @Test
        fun `a held bus keeps its place ahead of a bus that just pulled in`() {
            val memory = memoryOf(r326.id, "1:1" to entry("17:40:00", "17:49:00", lastSeen = "17:45:00"))
            val gap = poll(at("17:45:15"), memory, listOf(parked("1:2", "0:01")))
            assertEquals(DepartureState.Waiting(VehicleId("1:1"), at("17:49:00")), gap.rowAt(at("17:49:00"))?.state)
            assertEquals(DepartureState.Waiting(VehicleId("1:2"), at("18:07:00")), gap.rowAt(at("18:07:00"))?.state)
        }
    }

    private companion object {
        val TURNAROUND: Duration = PredictionRules.Default.minTurnaround
        const val POLL_SECONDS = 15L
    }
}
