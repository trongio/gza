package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.Synthetic.boardRow
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.moving
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.positions
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.at
import ge.hackerman.gza.core.predict.testing.memoryOf
import ge.hackerman.gza.core.predict.testing.of
import ge.hackerman.gza.core.predict.testing.tbilisi
import ge.hackerman.gza.core.predict.testing.waitingVehicle
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DeparturePredictorTest {
    private val r326 = route("326")
    private val r301 = route("301")
    private val monday = "2026-09-28"

    private fun predictAt(
        local: String,
        vararg routes: RouteSnapshot,
        board: StopBoard? = null,
        memory: LayoverMemory = LayoverMemory.Empty
    ) = DeparturePredictor(Clock.fixed(tbilisi(local).toInstant(), ZoneId.of("UTC")))
        .predict(StopRequest(home, routes.toList(), board, memory))

    private fun live(local: String) = tbilisi(local).toInstant()

    private fun remembered(vehicle: String, firstSeen: String, waitingFor: String? = null) = LayoverMemory(
        mapOf(
            ParkedKey(r326.id, VehicleId(vehicle)) to ParkedVehicle(
                PatternSuffix("0:01"),
                tbilisi(firstSeen).toInstant(),
                waitingFor?.let(::tbilisi)
            )
        )
    )

    @Test
    fun `a parked bus labelled with the other pattern counts for the one that starts here`() {
        val snapshot = RouteSnapshot(
            r326,
            listOf(
                schedule(r326, "0:01", listOf("17:49", "18:07")),
                schedule(r326, "1:01", listOf("17:40"), position = 3)
            ),
            positions = positions(r326, live("${monday}T17:32:00"), parked("1:3046", pattern = "1:01"))
        )
        val result = predictAt("${monday}T17:32:00", snapshot)
        assertEquals("1:3046", result.departures.at("326", "17:49").waitingVehicle)
        assertEquals(PatternSuffix("0:01"), result.memory.of("1:3046").pattern)
    }

    @Test
    fun `a relabelled bus goes to the pattern that leaves here next`() {
        val now = "${monday}T17:32:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(
                schedule(r326, "0:01", listOf("17:55")),
                schedule(r326, "0:02", listOf("17:40")),
                schedule(r326, "0:03", listOf("7:00"), from = DayOfWeek.MONDAY, to = DayOfWeek.MONDAY),
                schedule(r326, "1:01", listOf("17:35"), position = 3)
            ),
            positions = positions(r326, live(now), parked("1:3046", pattern = "1:01"))
        )
        val result = predictAt(now, snapshot)
        assertEquals("1:3046", result.departures.at("326", "17:40").waitingVehicle)
        assertEquals(PatternSuffix("0:02"), result.memory.of("1:3046").pattern)
        assertEquals(DepartureState.TimetableOnly, result.departures.at("326", "17:55").state)
    }

    @Test
    fun `a relabelled bus keeps the pattern it was counted for when that one starts here`() {
        val now = "${monday}T17:32:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(
                schedule(r326, "0:01", listOf("17:40")),
                schedule(r326, "0:02", listOf("17:55")),
                schedule(r326, "1:01", listOf("17:35"), position = 3)
            ),
            positions = positions(r326, live(now), parked("1:3046", pattern = "1:01"))
        )
        val counted = memoryOf(
            r326.id,
            "1:3046" to ParkedVehicle(PatternSuffix("0:02"), tbilisi("${monday}T17:20:00").toInstant(), null)
        )
        val result = predictAt(now, snapshot, memory = counted)
        assertEquals("1:3046", result.departures.at("326", "17:55").waitingVehicle)
        assertEquals(DepartureState.NoBusYet, result.departures.at("326", "17:40").state)
        assertEquals(PatternSuffix("0:02"), result.memory.of("1:3046").pattern)

        // A remembered pattern that does not start here either falls back to the next to leave.
        val gone = memoryOf(
            r326.id,
            "1:3046" to ParkedVehicle(PatternSuffix("1:01"), tbilisi("${monday}T17:20:00").toInstant(), null)
        )
        assertEquals(
            "1:3046",
            predictAt(now, snapshot, memory = gone).departures.at("326", "17:40").waitingVehicle
        )
    }

    @Test
    fun `a parked bus at a stop that starts no pattern is ignored`() {
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:40"), position = 2)),
            positions = positions(r326, live("${monday}T17:32:00"), parked("1:3046"))
        )
        val result = predictAt("${monday}T17:32:00", snapshot)
        assertTrue(result.departures.all { it.state == DepartureState.TimetableOnly })
        assertEquals(LayoverMemory.Empty, result.memory)
    }

    @Test
    fun `more parked buses than rows left today spill into tomorrow`() {
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("7:55", "22:19", "22:55"))),
            positions = positions(r326, live("${monday}T22:00:00"), parked("1:1"), parked("1:2"), parked("1:3"))
        )
        val rows = predictAt("${monday}T22:00:00", snapshot).departures
        assertEquals("1:1", rows[0].waitingVehicle)
        assertEquals("1:2", rows[1].waitingVehicle)
        assertEquals(tbilisi("2026-09-29T07:55:00"), rows[2].scheduled)
        assertEquals("1:3", rows[2].waitingVehicle)
        assertEquals(DepartureState.TimetableOnly, rows[3].state)
    }

    @Test
    fun `turnaround floor runs from the first sighting`() {
        val now = "${monday}T17:48:30"
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:49", "18:07"))),
            positions = positions(r326, live(now), parked("1:3046"))
        )
        val seenAt1748 = predictAt(now, snapshot, memory = remembered("1:3046", "${monday}T17:48:00"))
        assertEquals(tbilisi("${monday}T17:50:00"), seenAt1748.departures.at("326", "17:49").predicted)

        val firstPoll = predictAt(now, snapshot)
        assertEquals(tbilisi("${monday}T17:50:30"), firstPoll.departures.at("326", "17:49").predicted)

        val longWait = predictAt(now, snapshot, memory = remembered("1:3046", "${monday}T17:20:00"))
        assertEquals(tbilisi("${monday}T17:49:00"), longWait.departures.at("326", "17:49").predicted)
        assertEquals(tbilisi("${monday}T17:20:00").toInstant(), longWait.memory.vehicles.values.single().firstSeen)
    }

    @Test
    fun `a moving bus at the stop is not waiting`() {
        val now = "${monday}T17:45:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:49", "18:07"))),
            positions = positions(r326, live(now), moving("1:3046"))
        )
        val result = predictAt(now, snapshot)
        assertEquals(DepartureState.NoBusYet, result.departures.at("326", "17:49").state)
        assertEquals(LayoverMemory.Empty, result.memory)
    }

    @Test
    fun `board row goes on the next row of its pattern only`() {
        val now = "${monday}T17:32:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(
                schedule(r326, "0:01", listOf("17:31", "17:49", "18:07")),
                schedule(r326, "1:01", listOf("17:40", "17:58"), position = 2)
            )
        )
        val otherPattern = StopBoard(home.id, live(now), listOf(boardRow("326", "1:01", 3)))
        val rows = predictAt(now, snapshot, board = otherPattern).departures
        assertNull(rows.at("326", "17:49").boardHint)
        assertEquals(3, rows.at("326", "17:40").boardHint?.realtimeMinutes)
        assertEquals(1, rows.count { it.boardHint != null })
    }

    @Test
    fun `a board row with no pattern goes on the route's single next row`() {
        val now = "${monday}T17:32:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(
                schedule(r326, "0:01", listOf("17:31", "17:49", "18:07")),
                schedule(r326, "1:01", listOf("17:40", "17:58"), position = 2)
            )
        )
        val anyPattern = StopBoard(home.id, live(now), listOf(boardRow("326", null, 5, scheduled = 7)))
        val hinted = predictAt(now, snapshot, board = anyPattern).departures.filter { it.boardHint != null }
        assertEquals(listOf("17:40"), hinted.map { it.scheduled.toLocalTime().toString() })
        assertEquals(BoardHint(5, 7, live(now)), hinted.single().boardHint)

        // A row of the pattern itself wins its slot; the patternless row does not move elsewhere.
        val both = StopBoard(
            home.id,
            live(now),
            listOf(boardRow("326", null, 5), boardRow("326", "1:01", 8), boardRow("326", "0:01", 17))
        )
        val rows = predictAt(now, snapshot, board = both).departures
        assertEquals(8, rows.at("326", "17:40").boardHint?.realtimeMinutes)
        assertEquals(17, rows.at("326", "17:49").boardHint?.realtimeMinutes)
        assertEquals(2, rows.count { it.boardHint != null })
    }

    @Test
    fun `board rows of another route or kind are not attached`() {
        val now = "${monday}T17:32:00"
        val metro = route("1", id = "1:Metro_1", kind = TransportKind.METRO)
        val bus1 = route("1", id = "1:R1")
        val board = StopBoard(home.id, live(now), listOf(boardRow("1", null, 4, kind = TransportKind.METRO)))
        val rows = predictAt(
            now,
            RouteSnapshot(metro, listOf(schedule(metro, "0:01", listOf("17:40")))),
            RouteSnapshot(bus1, listOf(schedule(bus1, "0:01", listOf("17:41")))),
            RouteSnapshot(r326, listOf(schedule(r326, "0:01", listOf("17:42")))),
            board = board
        ).departures
        assertEquals(4, rows.first { it.routeId == metro.id }.boardHint?.realtimeMinutes)
        assertNull(rows.first { it.routeId == bus1.id }.boardHint)
        assertNull(rows.first { it.routeId == r326.id }.boardHint)
    }

    @Test
    fun `a bus board row is not attached to a cable car of the same number`() {
        val now = "${monday}T17:32:00"
        val cableCar = route("1", id = "1:Gondola_1", kind = TransportKind.CABLE_CAR)
        val board = StopBoard(home.id, live(now), listOf(boardRow("1", null, 4)))
        val rows = predictAt(
            now,
            RouteSnapshot(cableCar, listOf(schedule(cableCar, "0:01", listOf("17:40")))),
            board = board
        ).departures
        assertNull(rows.first().boardHint)
    }

    @Test
    fun `the board never changes a time`() {
        val now = "${monday}T17:32:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:31", "17:49", "18:07"))),
            positions = positions(r326, live(now), parked("1:3046"))
        )
        val board = StopBoard(home.id, live(now), listOf(boardRow("326", "0:01", 0, scheduled = -76)))
        val without = predictAt(now, snapshot)
        val with = predictAt(now, snapshot, board = board)
        assertEquals(without.departures, with.departures.map { it.copy(boardHint = null) })
        assertEquals(without.memory, with.memory)
    }

    @Test
    fun `no bus yet only for the next first stop row and only within 12 min`() {
        val snapshot = { now: String ->
            RouteSnapshot(
                r326,
                listOf(schedule(r326, "0:01", listOf("17:49", "18:07"))),
                positions = positions(r326, live(now))
            )
        }
        val at1737 = predictAt("${monday}T17:37:00", snapshot("${monday}T17:37:00")).departures
        assertEquals(DepartureState.NoBusYet, at1737.at("326", "17:49").state)
        assertEquals(DepartureState.TimetableOnly, at1737.at("326", "18:07").state)
        assertEquals(tbilisi("${monday}T17:49:00"), at1737.at("326", "17:49").predicted)

        val at1736 = predictAt("${monday}T17:36:59", snapshot("${monday}T17:36:59")).departures
        assertEquals(DepartureState.TimetableOnly, at1736.at("326", "17:49").state)

        val offline = predictAt(
            "${monday}T17:40:00",
            RouteSnapshot(r326, snapshot("${monday}T17:40:00").schedules)
        ).departures
        assertEquals(DepartureState.TimetableOnly, offline.at("326", "17:49").state)
    }

    @Test
    fun `no bus yet is not given at a downstream stop`() {
        val now = "${monday}T17:40:00"
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:49"), position = 2)),
            positions = positions(r326, live(now))
        )
        assertEquals(DepartureState.TimetableOnly, predictAt(now, snapshot).departures.at("326", "17:49").state)
    }

    @Test
    fun `ties sort by route number then pattern`() {
        val r36 = route("36")
        val rows = predictAt(
            "${monday}T17:00:00",
            RouteSnapshot(
                r326,
                listOf(schedule(r326, "1:01", listOf("17:10")), schedule(r326, "0:01", listOf("17:10")))
            ),
            RouteSnapshot(r301, listOf(schedule(r301, "0:01", listOf("17:10")))),
            RouteSnapshot(r36, listOf(schedule(r36, "0:01", listOf("17:10"))))
        ).departures.take(4)
        assertEquals(
            listOf("36" to "0:01", "301" to "0:01", "326" to "0:01", "326" to "1:01"),
            rows.map { it.routeShortName to it.pattern.value }
        )
    }

    @Test
    fun `waiting rows sort by their predicted time`() {
        val now = "${monday}T17:48:30"
        val rows = predictAt(
            now,
            RouteSnapshot(
                r326,
                listOf(schedule(r326, "0:01", listOf("17:49"))),
                positions = positions(r326, live(now), parked("1:3046"))
            ),
            RouteSnapshot(r301, listOf(schedule(r301, "0:01", listOf("17:50"))))
        ).departures
        assertEquals(listOf("301", "326"), rows.take(2).map { it.routeShortName })
    }

    @Test
    fun `the clock zone does not matter`() {
        val instant = tbilisi("${monday}T17:32:00").toInstant()
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:49", "18:07"))),
            positions = positions(r326, instant, parked("1:3046"))
        )
        val request = StopRequest(home, listOf(snapshot))
        val utc = DeparturePredictor(Clock.fixed(instant, ZoneId.of("UTC"))).predict(request)
        val newYork = DeparturePredictor(Clock.fixed(instant, ZoneId.of("America/New_York"))).predict(request)
        assertEquals(utc, newYork)
        assertEquals(ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Tbilisi")), utc.now)
    }

    @Test
    fun `headsign comes from the route detail when there is one`() {
        val rows = predictAt(
            "${monday}T17:00:00",
            RouteSnapshot(r326, listOf(schedule(r326, "0:01", listOf("17:10"))))
        ).departures
        assertNull(rows.first().headsign)
    }
}
