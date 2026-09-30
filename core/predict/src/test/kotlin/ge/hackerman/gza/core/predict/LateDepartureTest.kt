package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.positions
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.at
import ge.hackerman.gza.core.predict.testing.of
import ge.hackerman.gza.core.predict.testing.remembers
import ge.hackerman.gza.core.predict.testing.tbilisi
import ge.hackerman.gza.core.predict.testing.tbilisiClock
import ge.hackerman.gza.core.predict.testing.waitingVehicle
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class LateDepartureTest {
    private val r326 = route("326")
    private val day = "2026-09-28"

    private fun memory(vararg entries: Triple<String, String, String?>) = LayoverMemory(
        entries.associate { (vehicle, firstSeen, waitingFor) ->
            ParkedKey(r326.id, VehicleId(vehicle)) to ParkedVehicle(
                PatternSuffix("0:01"),
                tbilisi("${day}T$firstSeen").toInstant(),
                waitingFor?.let { tbilisi("${day}T$it") }
            )
        }
    )

    private val waitingFor1749 = memory(Triple("1:3046", "17:40:00", "17:49:00"))

    private fun predict(time: String, memory: LayoverMemory, vararg vehicles: String): StopPrediction {
        val now = "${day}T$time"
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:31", "17:49", "18:07"))),
            positions = positions(r326, tbilisi(now).toInstant(), *vehicles.map { parked(it) }.toTypedArray())
        )
        return DeparturePredictor(tbilisiClock(now)).predict(StopRequest(home, listOf(snapshot), memory = memory))
    }

    private fun late(vehicle: String, leavesAt: String, lateBy: Duration) =
        DepartureState.Late(VehicleId(vehicle), tbilisi("${day}T$leavesAt"), lateBy)

    @Test
    fun `a bus still parked after its time is late and leaves a minute from now`() {
        val result = predict("17:50:30", waitingFor1749, "1:3046")
        val row = result.departures.at("326", "17:49")
        assertEquals(late("1:3046", "17:51:30", Duration.ofSeconds(150)), row.state)
        assertEquals(tbilisi("${day}T17:51:30"), row.predicted)
        assertEquals(DepartureState.TimetableOnly, result.departures.at("326", "18:07").state)
        assertEquals(tbilisi("${day}T17:49:00"), result.memory.of("1:3046").waitingFor)
    }

    @Test
    fun `the late time moves forward with each poll`() {
        val row = predict("17:52:00", waitingFor1749, "1:3046").departures.at("326", "17:49")
        assertEquals(late("1:3046", "17:53:00", Duration.ofMinutes(4)), row.state)
    }

    @Test
    fun `late for up to 10 whole minutes, then the bus waits for the next departure`() {
        val stillLate = predict("17:59:59", waitingFor1749, "1:3046").departures.at("326", "17:49")
        assertEquals(tbilisi("${day}T18:00:59"), stillLate.predicted)
        assertTrue(stillLate.state is DepartureState.Late)

        val dropped = predict("18:00:00", waitingFor1749, "1:3046")
        assertTrue(dropped.departures.none { it.scheduled == tbilisi("${day}T17:49:00") })
        assertEquals("1:3046", dropped.departures.at("326", "18:07").waitingVehicle)
        assertEquals(tbilisi("${day}T18:07:00"), dropped.memory.of("1:3046").waitingFor)
    }

    @Test
    fun `a bus that was never given a departure never claims a past one`() {
        val rows = predict("17:32:00", memory(Triple("1:3046", "17:20:00", null)), "1:3046").departures
        assertEquals(DepartureState.TimetableOnly, rows.at("326", "17:31").state)
        assertEquals("1:3046", rows.at("326", "17:49").waitingVehicle)
    }

    @Test
    fun `a late bus and a waiting bus share the stop`() {
        val both = memory(Triple("1:A", "17:20:00", "17:31:00"), Triple("1:B", "17:25:00", "17:49:00"))
        val rows = predict("17:32:00", both, "1:A", "1:B").departures
        assertEquals(late("1:A", "17:33:00", Duration.ofMinutes(2)), rows.at("326", "17:31").state)
        assertEquals("1:B", rows.at("326", "17:49").waitingVehicle)
    }

    @Test
    fun `a late bus that just pulled in still gets its turnaround`() {
        val justIn = memory(Triple("1:3046", "17:48:50", "17:49:00"))
        val row = predict("17:49:10", justIn, "1:3046").departures.at("326", "17:49")
        assertEquals(late("1:3046", "17:50:50", Duration.ofSeconds(110)), row.state)
    }

    @Test
    fun `a bus that left is no longer late`() {
        val result = predict("17:50:30", waitingFor1749, "1:9999")
        assertTrue(result.departures.none { it.state is DepartureState.Late })
        assertTrue(!result.memory.remembers(VehicleId("1:3046")))
    }

    @Test
    fun `waiting then late across two polls`() {
        val first = predict("17:48:30", LayoverMemory.Empty, "1:3046")
        assertEquals(tbilisi("${day}T17:50:30"), first.departures.at("326", "17:49").predicted)

        val second = predict("17:51:00", first.memory, "1:3046")
        val row = second.departures.at("326", "17:49")
        assertEquals(late("1:3046", "17:52:00", Duration.ofMinutes(3)), row.state)
    }
}
