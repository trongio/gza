package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.predict.testing.Synthetic.HOME
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.clock
import ge.hackerman.gza.core.predict.testing.tbilisi
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The headline rule over whole polling sessions: seeded random sequences of polls at one
 * terminus where buses park, flicker out of the feed, get relabelled, get listed twice, drive
 * past, and the feed goes offline, stale or skewed, some sessions across midnight. The engine
 * is fed its own memory back each poll, as T07 will.
 *
 * The oracle knows only what the rules promise: a sighting lasts until a live poll misses the
 * bus for more than [PredictionRules.memoryDropout] after it was last seen, or until it
 * returns more than [PredictionRules.memoryMaxAge] later. A bus is never shown leaving before
 * max(timetable, that sighting's start + [PredictionRules.minTurnaround]).
 */
class HeadlineSessionPropertyTest {
    private val rules = PredictionRules.Default
    private val random = Random(20260930)
    private val r326 = route("326")
    private val first = PatternSuffix("0:01")
    private val second = PatternSuffix("0:02")
    private val passing = PatternSuffix("1:01")
    private val labels = listOf(first, second, passing)
    private val schedules = listOf(
        schedule(r326, "0:01", headways(start = 5 * 60 + 30, min = 6, max = 18)),
        schedule(r326, "0:02", headways(start = 6 * 60 + 10, min = 25, max = 50)),
        schedule(r326, "1:01", headways(start = 5 * 60 + 45, min = 8, max = 20), position = 2)
    )
    private val far = LatLon(HOME.lat + 0.0054, HOME.lon)
    private val buses = (1..4).map { VehicleId("1:$it") }
    private val counts = mutableMapOf<String, Int>()

    @Test
    fun `a parked bus never leaves before max of its timetable and its sighting plus 2 min over 300 sessions`() {
        repeat(SESSIONS) { session -> runSession(session) }
        // The stream must reach the interesting cases, or the invariant proves little.
        mapOf(
            "Waiting" to 2_000,
            "Late" to 300,
            "reset" to 200,
            "dropped" to 200,
            "dropoutKept" to 200,
            "offline" to 200,
            "relabelled" to 500,
            "midnight" to 200
        ).forEach { (name, min) ->
            assertTrue(counts.getOrDefault(name, 0) >= min, "too few $name in $counts")
        }
    }

    private enum class Where { Absent, Parked, Moving, Far }

    private class Bus(var where: Where, var label: PatternSuffix)

    private class Sighting(var start: Instant? = null, var lastSeen: Instant? = null)

    private fun runSession(session: Int) {
        var now = startOf(session)
        var memory = LayoverMemory.Empty
        val state = buses.associateWith { Bus(Where.entries.random(random), labels.random(random)) }
        val sightings = buses.associateWith { Sighting() }
        val promised = mutableSetOf<Pair<VehicleId, Instant>>()

        repeat(random.nextInt(MIN_POLLS, MAX_POLLS)) { poll ->
            state.values.forEach { it.step() }
            val positions = feed(now, state)
            val live = positions != null && positions.isFresh(now)
            if (!live) bump("offline")
            if (now.hour == 0 || (now.hour == 23 && now.minute >= 50)) bump("midnight")

            val result = DeparturePredictor(now.toInstant().clock()).predict(
                StopRequest(home, listOf(RouteSnapshot(r326, schedules, positions = positions)), memory = memory)
            )
            val context = "session $session poll $poll at $now"
            if (live) track(now.toInstant(), state, sightings, memory, result.memory)
            check(result, now, sightings, promised, context)

            memory = result.memory
            now = now.plus(nextStep())
        }
    }

    /** Updates the oracle's sightings from a live poll, the way the rules describe them. */
    private fun track(
        now: Instant,
        state: Map<VehicleId, Bus>,
        sightings: Map<VehicleId, Sighting>,
        before: LayoverMemory,
        after: LayoverMemory
    ) {
        state.forEach { (id, bus) ->
            val sighting = sightings.getValue(id)
            val key = ParkedKey(r326.id, id)
            when {
                bus.where == Where.Parked -> sighting.seen(now)
                sighting.missed(now) -> bump("dropped")
                sighting.start != null && key in before.vehicles && key in after.vehicles -> bump("dropoutKept")
            }
        }
    }

    private fun Sighting.seen(now: Instant) {
        val last = lastSeen
        if (start == null || last == null || Duration.between(last, now) > rules.memoryMaxAge) {
            if (start != null) bump("reset")
            start = now
        }
        lastSeen = now
    }

    /** True when a live poll without the bus ends its sighting. */
    private fun Sighting.missed(now: Instant): Boolean {
        val ended = lastSeen?.let { Duration.between(it, now) > rules.memoryDropout } ?: false
        if (ended) {
            start = null
            lastSeen = null
        }
        return ended
    }

    private fun check(
        result: StopPrediction,
        now: ZonedDateTime,
        sightings: Map<VehicleId, Sighting>,
        promised: MutableSet<Pair<VehicleId, Instant>>,
        context: String
    ) {
        val shown = mutableListOf<VehicleId>()
        result.departures.forEach { row ->
            val (vehicle, leavesAt) = when (val s = row.state) {
                is DepartureState.Waiting -> {
                    bump("Waiting")
                    assertFalse(s.leavesAt.isBefore(row.scheduled), "left before its timetable, $context: $row")
                    promised += s.vehicleId to row.scheduled.toInstant()
                    s.vehicleId to s.leavesAt
                }

                is DepartureState.Late -> {
                    bump("Late")
                    assertFalse(s.leavesAt.isBefore(now.plus(rules.lateStep)), "late but leaving now, $context: $row")
                    assertTrue(row.scheduled.isBefore(now), "late for a future row, $context: $row")
                    // Never a cold-start late (PLAN.md 2.0): it was shown waiting for this row first.
                    assertTrue(
                        s.vehicleId to row.scheduled.toInstant() in promised,
                        "late with no promise, $context: $row"
                    )
                    s.vehicleId to s.leavesAt
                }

                else -> return@forEach
            }
            shown += vehicle
            assertFalse(leavesAt.isBefore(now), "arriving now, $context: $row")
            val start = assertNotNull(sightings.getValue(vehicle).start, "shown but not parked, $context: $row")
            assertFalse(
                leavesAt.toInstant().isBefore(start.plus(rules.minTurnaround)),
                "turnaround waived, $context: $row"
            )
            val remembered = result.memory.vehicles.getValue(ParkedKey(r326.id, vehicle))
            assertFalse(remembered.firstSeen.isBefore(start), "remembers an older sighting, $context: $remembered")
        }
        assertEquals(shown.distinct(), shown, "one row per bus, $context")
    }

    private fun Bus.step() {
        if (random.nextInt(100) < MOVE_PERCENT) {
            where = when (random.nextInt(10)) {
                in 0..3 -> Where.Parked
                in 4..6 -> Where.Absent
                7, 8 -> Where.Moving
                else -> Where.Far
            }
        }
        if (random.nextInt(100) < RELABEL_PERCENT) {
            label = labels.random(random)
            bump("relabelled")
        }
    }

    /** Null when offline; sometimes stale or stamped by a skewed clock. */
    private fun feed(now: ZonedDateTime, state: Map<VehicleId, Bus>): RoutePositions? {
        if (random.nextInt(100) < OFFLINE_PERCENT) return null
        val stamp = when (random.nextInt(100)) {
            in 0..3 -> now.plusSeconds(random.nextLong(-SKEW_RANGE_SECONDS, SKEW_RANGE_SECONDS))
            in 4..5 -> now.plusSeconds(random.nextLong(115, 125))
            in 6..7 -> now.minusSeconds(random.nextLong(115, 125))
            else -> now
        }
        val vehicles = state.flatMap { (id, bus) ->
            when (bus.where) {
                Where.Absent -> emptyList()

                Where.Parked -> listOf(VehiclePosition(id, bus.label, HOME, null, null)) +
                    // The feed sometimes lists one bus under two patterns.
                    if (random.nextInt(100) < DUPLICATE_PERCENT) {
                        listOf(VehiclePosition(id, labels.random(random), HOME, null, null))
                    } else {
                        emptyList()
                    }

                Where.Moving -> listOf(VehiclePosition(id, bus.label, HOME, 90.0, StopId("1:969")))

                Where.Far -> listOf(VehiclePosition(id, bus.label, far, null, null))
            }
        }.shuffled(random)
        return RoutePositions(r326.id, stamp.toInstant(), vehicles)
    }

    private fun RoutePositions.isFresh(now: ZonedDateTime): Boolean {
        val age = Duration.between(fetchedAt, now.toInstant())
        return age <= rules.maxPositionsAge && age >= rules.maxClockSkew.negated()
    }

    private fun nextStep(): Duration = when (random.nextInt(100)) {
        in 0..69 -> Duration.ofSeconds(15)

        in 70..79 -> Duration.ofSeconds(random.nextLong(1, 15))

        in 80..91 -> Duration.ofSeconds(random.nextLong(30, 120))

        in 92..97 -> Duration.ofSeconds(random.nextLong(55, 70))

        // The screen was left and opened again with the memory kept.
        else -> Duration.ofMinutes(random.nextLong(5, 40))
    }

    private fun startOf(session: Int): ZonedDateTime {
        val day = tbilisi("2026-09-27T00:00:00").plusDays(random.nextLong(0, 16))
        return if (session % 4 == 0) {
            day.withHour(23).withMinute(35).plusSeconds(random.nextLong(0, 20 * 60))
        } else {
            day.withHour(5).plusSeconds(random.nextLong(0, 19 * 3600))
        }
    }

    /** Times from [start] minutes to 24:40 with random headways, as the gateway writes them. */
    private fun headways(start: Int, min: Int, max: Int): List<String> {
        val times = Random(start)
        return generateSequence(start) { it + times.nextInt(min, max + 1) }
            .takeWhile { it <= LAST_MINUTE }
            .map { "%d:%02d".format(it / 60, it % 60) }
            .toList()
    }

    private fun bump(name: String) {
        counts[name] = counts.getOrDefault(name, 0) + 1
    }

    private companion object {
        const val SESSIONS = 300
        const val MIN_POLLS = 60
        const val MAX_POLLS = 160
        const val MOVE_PERCENT = 12
        const val RELABEL_PERCENT = 8
        const val OFFLINE_PERCENT = 6
        const val DUPLICATE_PERCENT = 10
        const val SKEW_RANGE_SECONDS = 180L
        const val LAST_MINUTE = 24 * 60 + 40
    }
}
