package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.positions
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.tbilisi
import ge.hackerman.gza.core.predict.testing.tbilisiClock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LayoverMemoryFoldTest {
    private val r326 = route("326")
    private val r551 = route("551", id = "1:minibusR24579")
    private val day = "2026-09-28"

    private fun instant(time: String): Instant = tbilisi("${day}T$time").toInstant()

    private fun snapshot(route: Route, time: String, vehicles: List<String>?) = RouteSnapshot(
        route,
        listOf(schedule(route, "0:01", listOf("17:49", "18:07"))),
        positions = vehicles?.let { ids -> positions(route, instant(time), *ids.map { parked(it) }.toTypedArray()) }
    )

    private fun poll(
        time: String,
        memory: LayoverMemory,
        vehicles326: List<String>?,
        vehicles551: List<String>? = null
    ): LayoverMemory = DeparturePredictor(tbilisiClock("${day}T$time")).predict(
        StopRequest(
            home,
            listOf(snapshot(r326, time, vehicles326), snapshot(r551, time, vehicles551)),
            memory = memory
        )
    ).memory

    @Test
    fun `first sighting is now and later polls keep it`() {
        val first = poll("17:30:00", LayoverMemory.Empty, listOf("1:1"))
        assertEquals(instant("17:30:00"), first.vehicles.getValue(VehicleId("1:1")).firstSeen)

        val second = poll("17:30:15", first, listOf("1:1"))
        assertEquals(instant("17:30:00"), second.vehicles.getValue(VehicleId("1:1")).firstSeen)
        assertEquals(tbilisi("${day}T17:49:00"), second.vehicles.getValue(VehicleId("1:1")).waitingFor)
    }

    @Test
    fun `a bus that leaves is dropped`() {
        val first = poll("17:30:00", LayoverMemory.Empty, listOf("1:1", "1:2"))
        val second = poll("17:30:15", first, listOf("1:2"))
        assertEquals(setOf(VehicleId("1:2")), second.vehicles.keys)
    }

    @Test
    fun `an offline poll keeps what earlier polls learned about that route`() {
        val first = poll("17:30:00", LayoverMemory.Empty, listOf("1:1"), listOf("1:5"))
        val offline326 = poll("17:30:15", first, vehicles326 = null, vehicles551 = listOf("1:5"))
        assertEquals(first.vehicles.getValue(VehicleId("1:1")), offline326.vehicles.getValue(VehicleId("1:1")))
        assertEquals(setOf(VehicleId("1:1"), VehicleId("1:5")), offline326.vehicles.keys)

        val bothOffline = poll("17:30:30", first, vehicles326 = null)
        assertEquals(first, bothOffline)
    }

    @Test
    fun `entries of routes not asked about are kept`() {
        val elsewhere = ParkedVehicle(route("999").id, PatternSuffix("0:01"), instant("17:00:00"), null)
        val memory = LayoverMemory(mapOf(VehicleId("1:77") to elsewhere))
        assertEquals(elsewhere, poll("17:30:00", memory, emptyList()).vehicles.getValue(VehicleId("1:77")))
    }

    @Test
    fun `a first sighting in the future counts as now`() {
        val future = LayoverMemory(
            mapOf(VehicleId("1:1") to ParkedVehicle(r326.id, PatternSuffix("0:01"), instant("17:45:00"), null))
        )
        val folded = poll("17:30:00", future, listOf("1:1"))
        assertEquals(instant("17:30:00"), folded.vehicles.getValue(VehicleId("1:1")).firstSeen)
    }

    @Test
    fun `history of the same vehicle id on another route is not reused`() {
        val other = LayoverMemory(
            mapOf(VehicleId("1:1") to ParkedVehicle(r551.id, PatternSuffix("0:01"), instant("17:00:00"), null))
        )
        val folded = poll("17:30:00", other, listOf("1:1"), vehicles551 = emptyList())
        val entry = folded.vehicles.getValue(VehicleId("1:1"))
        assertEquals(r326.id, entry.routeId)
        assertEquals(instant("17:30:00"), entry.firstSeen)
    }

    @Test
    fun `a parked bus with no departure left to take is remembered without one`() {
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:49"))),
            positions = positions(r326, instant("17:30:00"), parked("1:1"), parked("1:2"), parked("1:3"))
        )
        val memory = DeparturePredictor(tbilisiClock("${day}T17:30:00"))
            .predict(StopRequest(home, listOf(snapshot)))
            .memory
        assertNull(memory.vehicles.getValue(VehicleId("1:3")).waitingFor)
    }
}
