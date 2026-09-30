package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.BoardArrival
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import ge.hackerman.gza.core.predict.testing.Snapshots.schedule
import ge.hackerman.gza.core.predict.testing.Snapshots.snapshot
import ge.hackerman.gza.core.predict.testing.Snapshots.stop970
import ge.hackerman.gza.core.predict.testing.clock
import ge.hackerman.gza.core.predict.testing.tbilisi
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The invariants of the engine over a seeded stream of random stops, clocks, buses and
 * memories on the real 326 and 551 timetables at 1:970: two weeks around the listed week, so
 * both rollovers and the weekday fallback are covered.
 */
class DeparturePredictorPropertyTest {
    private val random = Random(20260928)
    private val start = tbilisi("2026-09-27T00:00:00")
    private val end = tbilisi("2026-10-13T00:00:00")
    private val routes = listOf(
        snapshot("326", listOf(schedule("326", "0-01"), schedule("326", "1-01"))),
        snapshot("551", listOf(schedule("551", "0-01")))
    )
    private val labels = listOf(PatternSuffix("0:01"), PatternSuffix("1:01"))
    private val memories = mutableListOf(LayoverMemory.Empty)

    @Test
    fun `invariants hold for 2000 random cases`() {
        var previous: Pair<StopRequest, StopPrediction>? = null
        repeat(CASES) { case ->
            val (now, request) = previous?.takeIf { random.nextInt(5) < 2 }?.let { followUp(it.first, it.second) }
                ?: freshCase()
            val predictor = DeparturePredictor(now.toInstant().clock())
            val result = predictor.predict(request)
            val context = "case $case at $now"

            assertEquals(result, predictor.predict(request), "deterministic, $context")
            assertSorted(result.departures, context)
            assertStates(result, request, now, context)
            assertMemory(result, request, now, context)
            assertBoardIsOnlyAHint(predictor, request, result, now, context)
            assertLeaveBy(result.departures, context)
            memories += result.memory
            previous = request to result
            result.departures.forEach { row ->
                val name = row.state::class.simpleName.orEmpty()
                seen[name] = seen.getOrDefault(name, 0) + 1
            }
        }
        // The stream must actually reach every state, or the invariants above prove little.
        listOf("Waiting", "Late", "NoBusYet", "TimetableOnly").forEach { state ->
            assertTrue(seen.getOrDefault(state, 0) >= MIN_ROWS_PER_STATE, "too few $state rows in $seen")
        }
    }

    private val seen = mutableMapOf<String, Int>()

    private fun freshCase(): Pair<ZonedDateTime, StopRequest> {
        val now = start.plusSeconds(random.nextLong(Duration.between(start, end).seconds))
        val snapshots = routes.map { it.copy(positions = randomPositions(it, now)) }
        return now to StopRequest(stop970, snapshots, memory = randomMemory())
    }

    /**
     * The next poll of the same stop: the same buses a little later, fed the memory the last
     * poll returned. This is how buses become late, which random clocks alone rarely reach.
     */
    private fun followUp(request: StopRequest, result: StopPrediction): Pair<ZonedDateTime, StopRequest> {
        val now = result.now.plusSeconds(random.nextLong(FOLLOW_UP_MIN_SECONDS, FOLLOW_UP_MAX_SECONDS))
        val snapshots = request.routes.map { snapshot ->
            snapshot.copy(positions = snapshot.positions?.copy(fetchedAt = now.toInstant()))
        }
        return now to request.copy(routes = snapshots, memory = result.memory)
    }

    private fun assertSorted(rows: List<PredictedDeparture>, context: String) {
        rows.zipWithNext().forEach { (a, b) ->
            val key = { d: PredictedDeparture ->
                listOf(d.predicted.toInstant(), d.scheduled.toInstant())
            }
            val (ap, asch) = key(a)
            val (bp, bsch) = key(b)
            assertFalse(ap > bp || (ap == bp && asch > bsch), "sorted, $context: $a then $b")
            if (ap == bp && asch == bsch) {
                val an = a.routeShortName.length to a.routeShortName
                val bn = b.routeShortName.length to b.routeShortName
                assertTrue(compareValuesBy(an, bn, { it.first }, { it.second }) <= 0, "route order, $context")
            }
        }
    }

    private fun assertStates(result: StopPrediction, request: StopRequest, now: ZonedDateTime, context: String) {
        val earliest = now.minusMinutes(1)
        val vehicles = mutableListOf<VehicleId>()
        result.departures.forEach { row ->
            assertFalse(row.predicted.isBefore(earliest), "listed rows are not past, $context: $row")
            assertFalse(row.pattern == PatternSuffix("1:01"), "no arrivals at a last stop, $context: $row")
            when (val state = row.state) {
                is DepartureState.Waiting -> {
                    vehicles += state.vehicleId
                    assertEquals(state.leavesAt, row.predicted, context)
                    assertFalse(state.leavesAt.isBefore(row.scheduled), "never leaves early, $context: $row")
                    assertFalse(state.leavesAt.isBefore(now), "never arriving now, $context: $row")
                    if (ParkedKey(row.routeId, state.vehicleId) !in request.memory.vehicles) {
                        assertFalse(state.leavesAt.isBefore(now.plusMinutes(2)), "turnaround, $context: $row")
                    }
                }

                is DepartureState.Late -> {
                    vehicles += state.vehicleId
                    assertEquals(state.leavesAt, row.predicted, context)
                    assertTrue(state.leavesAt.isAfter(now), "late bus leaves after now, $context: $row")
                    assertEquals(Duration.between(row.scheduled, state.leavesAt), state.lateBy, context)
                }

                DepartureState.NoBusYet, DepartureState.TimetableOnly ->
                    assertEquals(row.scheduled, row.predicted, "timetable time, $context: $row")
            }
        }
        assertEquals(vehicles.distinct(), vehicles, "one row per bus, $context")
        assertTrue(vehicles.size <= parkedKeys(request, now).size, "no more buses than parked, $context")
    }

    private fun assertMemory(result: StopPrediction, request: StopRequest, now: ZonedDateTime, context: String) {
        val live = liveRoutes(request, now)
        val kept = request.memory.vehicles.keys.filter { it.routeId !in live }
        assertEquals(parkedKeys(request, now) + kept, result.memory.vehicles.keys, "memory keys, $context")
    }

    private fun assertBoardIsOnlyAHint(
        predictor: DeparturePredictor,
        request: StopRequest,
        result: StopPrediction,
        now: ZonedDateTime,
        context: String
    ) {
        val board = StopBoard(stop970.id, now.toInstant(), routes.map { randomBoardRow(it.route.shortName) })
        val withBoard = predictor.predict(request.copy(board = board))
        assertEquals(result.departures, withBoard.departures.map { it.copy(boardHint = null) }, "board, $context")
        assertEquals(result.memory, withBoard.memory, "board, $context")
    }

    private fun assertLeaveBy(rows: List<PredictedDeparture>, context: String) {
        rows.take(LEAVE_BY_SAMPLE).forEach { row ->
            val walk = random.nextInt(0, 61)
            val buffer = random.nextInt(0, 4)
            assertEquals(row.predicted.minusMinutes((walk + buffer).toLong()), row.leaveBy(walk, buffer), context)
        }
    }

    private fun liveRoutes(request: StopRequest, now: ZonedDateTime) = request.routes
        .mapNotNull { it.positions }
        .filter {
            val age = Duration.between(it.fetchedAt, now.toInstant())
            age <= PredictionRules.Default.maxPositionsAge && age >= PredictionRules.Default.maxClockSkew.negated()
        }
        .map { it.routeId }
        .toSet()

    private fun parkedKeys(request: StopRequest, now: ZonedDateTime): Set<ParkedKey> {
        val live = liveRoutes(request, now)
        return request.routes
            .mapNotNull { it.positions }
            .filter { it.routeId in live }
            .flatMap { positions ->
                positions.vehicles
                    .filter { it.isInLayoverAt(stop970.location) }
                    .map { ParkedKey(positions.routeId, it.vehicleId) }
            }
            .toSet()
    }

    private fun randomPositions(route: RouteSnapshot, now: ZonedDateTime): RoutePositions? {
        val fetchedAt = when (random.nextInt(6)) {
            0 -> return null

            1 -> now.minusMinutes(random.nextLong(2, 10))

            // The phone clock behind the gateway: a little is skew, a lot is not trusted.
            2 -> now.plusSeconds(random.nextLong(0, MAX_FUTURE_SECONDS))

            else -> now.minusSeconds(random.nextLong(0, 121))
        }
        val count = random.nextInt(0, 4)
        // A small id pool per route, so memories from earlier cases meet the same buses again.
        val ids = (0 until VEHICLE_POOL).shuffled(random).take(count)
        val vehicles = ids.map { id ->
            VehiclePosition(
                vehicleId = VehicleId("1:${route.route.shortName}$id"),
                pattern = labels.random(random),
                location = randomPointNear(stop970.location),
                headingDegrees = if (random.nextInt(3) == 0) random.nextDouble(0.0, 360.0) else null,
                nextStopId = if (random.nextInt(3) == 0) StopId("1:969") else null
            )
        }
        return RoutePositions(route.route.id, fetchedAt.toInstant(), vehicles)
    }

    private fun randomPointNear(center: LatLon): LatLon {
        val meters = random.nextDouble(0.0, MAX_OFFSET_METERS)
        val angle = random.nextDouble(0.0, 2 * Math.PI)
        val dLat = meters * cos(angle) / METERS_PER_DEGREE
        val dLon = meters * sin(angle) / (METERS_PER_DEGREE * cos(Math.toRadians(center.lat)))
        return LatLon(center.lat + dLat, center.lon + dLon)
    }

    private fun randomMemory(): LayoverMemory =
        if (random.nextBoolean()) memories.random(random) else LayoverMemory.Empty

    private fun randomBoardRow(number: String): BoardArrival {
        val minutes = { if (random.nextInt(4) == 0) null else random.nextInt(-120, 121) }
        val realtime = minutes()
        return BoardArrival(
            routeShortName = number,
            pattern = if (random.nextBoolean()) PatternSuffix("0:01") else null,
            headsign = null,
            color = null,
            kind = TransportKind.BUS,
            realtime = realtime != null,
            realtimeMinutesHint = realtime,
            scheduledMinutesHint = minutes()
        )
    }

    private companion object {
        const val CASES = 2_000
        const val VEHICLE_POOL = 5
        const val MAX_OFFSET_METERS = 800.0
        const val METERS_PER_DEGREE = 111_320.0
        const val LEAVE_BY_SAMPLE = 3
        const val MIN_ROWS_PER_STATE = 10
        const val FOLLOW_UP_MIN_SECONDS = 15L
        const val FOLLOW_UP_MAX_SECONDS = 900L
        const val MAX_FUTURE_SECONDS = 300L
    }
}
