package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.PredictFixtures
import ge.hackerman.gza.core.predict.testing.Snapshots
import ge.hackerman.gza.core.predict.testing.Snapshots.firstStop
import ge.hackerman.gza.core.predict.testing.Snapshots.positions
import ge.hackerman.gza.core.predict.testing.Snapshots.schedule
import ge.hackerman.gza.core.predict.testing.Snapshots.snapshot
import ge.hackerman.gza.core.predict.testing.Snapshots.stop970
import ge.hackerman.gza.core.predict.testing.at
import ge.hackerman.gza.core.predict.testing.clock
import ge.hackerman.gza.core.predict.testing.tbilisi
import ge.hackerman.gza.core.predict.testing.tbilisiClock
import ge.hackerman.gza.core.predict.testing.waitingVehicle
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** The headline rule on real captures: a bus parked at its first stop is waiting, never arriving. */
class TerminusFixtureTest {
    private val derived = "terminus/326-derived-1732"

    private fun board(path: String): StopBoard = PredictFixtures.board(path, stop970.id)

    private fun derived326(positions: RoutePositions? = positions("$derived/positions.json", "326")) = StopRequest(
        stop = stop970,
        routes = listOf(
            snapshot(
                "326",
                listOf(
                    PredictFixtures.schedule("$derived/schedule-0-01.json", Snapshots.routeId("326"), "0:01"),
                    schedule("326", "1-01")
                ),
                positions
            )
        ),
        board = board("$derived/arrival-times.json")
    )

    private fun waiting(vehicle: String, local: String) = DepartureState.Waiting(VehicleId(vehicle), tbilisi(local))

    @Test
    fun `parked 326 with the board at 0 waits until 17 49`() {
        val result = DeparturePredictor(tbilisiClock("2026-09-28T17:32:00")).predict(derived326())
        val rows = result.departures

        val next = rows.at("326", "17:49")
        assertEquals(waiting("1:3046", "2026-09-28T17:49:00"), next.state)
        assertEquals(tbilisi("2026-09-28T17:49:00"), next.predicted)
        assertEquals(0, next.boardHint?.realtimeMinutes)
        assertEquals("Baratashvili St", next.headsign)

        assertEquals(DepartureState.TimetableOnly, rows.at("326", "17:31").state)
        assertNull(rows.at("326", "17:31").boardHint)
        assertEquals(DepartureState.TimetableOnly, rows.at("326", "18:07").state)
        assertTrue(rows.none { it.pattern == PatternSuffix("1:01") })
        assertEquals(tbilisi("2026-09-28T17:31:00"), rows.first().scheduled)

        val memory = result.memory.vehicles.getValue(VehicleId("1:3046"))
        assertEquals(tbilisi("2026-09-28T17:49:00"), memory.waitingFor)
        assertEquals(PatternSuffix("0:01"), memory.pattern)
        assertEquals(Instant.parse("2026-09-28T13:32:00Z"), memory.firstSeen)
    }

    @Test
    fun `551 at 20 44 waits for 20 47 while the board says 0`() {
        val case = "terminus/551-20260928T2043"
        val request = StopRequest(
            stop = stop970,
            routes = listOf(
                snapshot(
                    "551",
                    listOf(PredictFixtures.schedule("$case/schedule-0-01.json", Snapshots.routeId("551"), "0:01")),
                    positions("$case/positions.json", "551")
                )
            ),
            board = board("$case/arrival-times.json")
        )
        val rows = DeparturePredictor(tbilisiClock("2026-09-28T20:44:48")).predict(request).departures

        assertEquals(waiting("1:981", "2026-09-28T20:47:00"), rows.at("551", "20:47").state)
        assertEquals(0, rows.at("551", "20:47").boardHint?.realtimeMinutes)
        assertEquals(1, rows.count { it.boardHint != null })
    }

    @Test
    fun `two parked 301 buses take 21 07 and 21 21`() {
        val case = "terminus/301-20260928T2102"
        val request = StopRequest(
            stop = stop970,
            routes = listOf(
                snapshot(
                    "301",
                    listOf(PredictFixtures.schedule("$case/schedule-0-01.json", Snapshots.routeId("301"), "0:01")),
                    positions("$case/positions.json", "301")
                )
            ),
            board = board("$case/arrival-times.json")
        )
        val rows = DeparturePredictor(tbilisiClock("2026-09-28T21:02:28")).predict(request).departures

        assertEquals(waiting("1:3868", "2026-09-28T21:07:00"), rows.at("301", "21:07").state)
        assertEquals(waiting("1:3871", "2026-09-28T21:21:00"), rows.at("301", "21:21").state)
        assertEquals(DepartureState.TimetableOnly, rows.at("301", "21:35").state)
    }

    @Test
    fun `memory from real snapshots keeps each 301 bus on its slot`() {
        val schedules = listOf(schedule("301", "0-01"))
        fun poll(path: String, local: String, memory: LayoverMemory): StopPrediction = DeparturePredictor(
            tbilisiClock(local)
        ).predict(StopRequest(stop970, listOf(snapshot("301", schedules, positions(path, "301"))), memory = memory))

        val first = poll("positions/301-20260928T2044.json", "2026-09-28T20:44:48", LayoverMemory.Empty)
        assertEquals("1:3870", first.departures.at("301", "20:53").waitingVehicle)
        assertEquals("1:3871", first.departures.at("301", "21:07").waitingVehicle)

        val second = poll("positions/301-20260928T2101.json", "2026-09-28T21:01:52", first.memory)
        assertTrue(VehicleId("1:3870") !in second.memory.vehicles)
        assertEquals("1:3871", second.departures.at("301", "21:07").waitingVehicle)
        assertEquals("1:3868", second.departures.at("301", "21:21").waitingVehicle)
        assertEquals(
            Instant.parse("2026-09-28T17:01:52Z"),
            second.memory.vehicles.getValue(VehicleId("1:3868")).firstSeen
        )
        assertEquals(
            Instant.parse("2026-09-28T16:44:48Z"),
            second.memory.vehicles.getValue(VehicleId("1:3871")).firstSeen
        )

        val third = poll("terminus/301-20260928T2102/positions.json", "2026-09-28T21:02:28", second.memory)
        assertEquals("1:3871", third.departures.at("301", "21:07").waitingVehicle)
        assertEquals("1:3868", third.departures.at("301", "21:21").waitingVehicle)
    }

    @Test
    fun `whole stop at 21 01, three routes, board at 0 never wins`() {
        val request = StopRequest(
            stop = stop970,
            routes = listOf(
                snapshot("301", listOf(schedule("301", "0-01")), positions("positions/301-20260928T2101.json", "301")),
                snapshot(
                    "326",
                    listOf(schedule("326", "0-01"), schedule("326", "1-01")),
                    positions("positions/326-20260928T2101.json", "326")
                ),
                snapshot("551", listOf(schedule("551", "0-01")), positions("positions/551-20260928T2101.json", "551"))
            ),
            board = board("arrival-times/1-970-en-20260928T2101.json")
        )
        val now = tbilisi("2026-09-28T21:01:54")
        val rows = DeparturePredictor(now.toInstant().clock()).predict(request).departures

        assertEquals(
            listOf(
                "301" to waiting("1:3868", "2026-09-28T21:07:00"),
                "326" to waiting("1:3046", "2026-09-28T21:07:00"),
                "551" to waiting("1:762", "2026-09-28T21:20:00"),
                "301" to waiting("1:3871", "2026-09-28T21:21:00")
            ),
            rows.take(4).map { it.routeShortName to it.state }
        )
        assertTrue(rows.none { it.predicted.isBefore(tbilisi("2026-09-28T21:07:00")) })
        assertTrue(rows.none { it.routeShortName == "551" && it.scheduled == tbilisi("2026-09-28T21:00:00") })
        assertEquals(0, rows.at("551", "21:20").boardHint?.realtimeMinutes)
        assertEquals(0, rows.at("301", "21:07").boardHint?.realtimeMinutes)
        assertEquals(2, rows.at("326", "21:07").boardHint?.realtimeMinutes)
    }

    @Test
    fun `far terminus of 551 at 20 44 counts the lay-by bus at 442 m`() {
        val request = StopRequest(
            stop = firstStop("551", "1-01"),
            routes = listOf(
                snapshot(
                    "551",
                    listOf(schedule("551", "1-01")),
                    positions("terminus/551-20260928T2043/positions.json", "551")
                )
            )
        )
        val rows = DeparturePredictor(tbilisiClock("2026-09-28T20:44:48")).predict(request).departures

        assertEquals("1:774", rows.at("551", "20:52").waitingVehicle)
        assertEquals("1:985", rows.at("551", "21:08").waitingVehicle)
        assertEquals(DepartureState.TimetableOnly, rows.at("551", "21:28").state)
    }

    @Test
    fun `far terminus of 551 at 21 01 counts a relabelled bus and not moving ones`() {
        val result = DeparturePredictor(tbilisiClock("2026-09-28T21:01:54")).predict(
            StopRequest(
                stop = firstStop("551", "1-01"),
                routes = listOf(
                    snapshot(
                        "551",
                        listOf(schedule("551", "1-01")),
                        positions("positions/551-20260928T2101.json", "551")
                    )
                )
            )
        )

        assertEquals("1:220", result.departures.at("551", "21:08").waitingVehicle)
        assertEquals("1:987", result.departures.at("551", "21:28").waitingVehicle)
        assertEquals(setOf("1:220", "1:987"), result.memory.vehicles.keys.map { it.value }.toSet())
    }

    @Test
    fun `472 waits at 1 977`() {
        val rows = DeparturePredictor(tbilisiClock("2026-09-28T21:01:55")).predict(
            StopRequest(
                stop = firstStop("472", "1-03"),
                routes = listOf(
                    snapshot(
                        "472",
                        listOf(schedule("472", "1-03")),
                        positions("positions/472-20260928T2101.json", "472")
                    )
                )
            )
        ).departures

        assertEquals(waiting("1:744", "2026-09-28T21:20:00"), rows.at("472", "21:20").state)
    }

    @Test
    fun `301 lay-by bus at 318 m counts`() {
        val rows = DeparturePredictor(tbilisiClock("2026-09-28T20:44:48")).predict(
            StopRequest(
                stop = firstStop("301", "1-01"),
                routes = listOf(
                    snapshot(
                        "301",
                        listOf(schedule("301", "1-01")),
                        positions("positions/301-20260928T2044.json", "301")
                    )
                )
            )
        ).departures

        assertEquals(waiting("1:3854", "2026-09-28T20:46:48"), rows.at("301", "20:46").state)
        assertEquals(tbilisi("2026-09-28T20:46:48"), rows.at("301", "20:46").predicted)
        assertEquals(waiting("1:3863", "2026-09-28T21:00:00"), rows.at("301", "21:00").state)
    }

    @Test
    fun `offline stop shows the timetable`() {
        val memory = LayoverMemory(
            mapOf(
                VehicleId("1:3046") to ParkedVehicle(
                    Snapshots.routeId("326"),
                    PatternSuffix("0:01"),
                    Instant.parse("2026-09-28T13:20:00Z"),
                    tbilisi("2026-09-28T17:49:00")
                )
            )
        )
        val result = DeparturePredictor(tbilisiClock("2026-09-28T17:32:00"))
            .predict(derived326(positions = null).copy(memory = memory))

        assertTrue(result.departures.all { it.state == DepartureState.TimetableOnly })
        assertTrue(result.departures.all { it.predicted == it.scheduled })
        assertEquals(memory, result.memory)
    }

    @Test
    fun `stale positions count as offline`() {
        val fresh = positions("$derived/positions.json", "326")
        val predictor = DeparturePredictor(tbilisiClock("2026-09-28T17:32:00"))

        val stale = fresh.copy(fetchedAt = fresh.fetchedAt.minus(Duration.ofSeconds(121)))
        val staleRows = predictor.predict(derived326(stale)).departures
        assertEquals(DepartureState.TimetableOnly, staleRows.at("326", "17:49").state)

        val edge = fresh.copy(fetchedAt = fresh.fetchedAt.minus(Duration.ofMinutes(2)))
        assertEquals("1:3046", predictor.predict(derived326(edge)).departures.at("326", "17:49").waitingVehicle)

        val ahead = fresh.copy(fetchedAt = fresh.fetchedAt.plus(Duration.ofMinutes(5)))
        assertEquals("1:3046", predictor.predict(derived326(ahead)).departures.at("326", "17:49").waitingVehicle)
    }
}
