package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class StopMappersTest {
    private val dto = StopDto("1:970", "970", "Ana Politkovskaia Street", 41.722055, 44.703114, "BUS")

    @Test
    fun `a full stop maps`() {
        val stop = requireNotNull(dto.toStopOrNull())
        assertEquals(StopId("1:970"), stop.id)
        assertEquals("970", stop.code)
        assertEquals("Ana Politkovskaia Street", stop.name)
        assertEquals(LatLon(41.722055, 44.703114), stop.location)
        assertEquals(TransportKind.BUS, stop.kind)
    }

    @Test
    fun `a missing name falls back to the code, then the local id`() {
        assertEquals("970", dto.copy(name = null).toStopOrNull()?.name)
        assertEquals("970", dto.copy(name = "  ").toStopOrNull()?.name)
        assertEquals("metro_1_1", dto.copy(id = "1:metro_1_1", name = null, code = null).toStopOrNull()?.name)
    }

    @Test
    fun `an invalid id or position drops the stop`() {
        assertNull(dto.copy(id = null).toStopOrNull())
        assertNull(dto.copy(id = "970").toStopOrNull())
        assertNull(dto.copy(lat = null).toStopOrNull())
        assertNull(dto.copy(lat = 95.0).toStopOrNull())
    }

    @Test
    fun `gondola stops are cable car stops without a code`() {
        val stop = dto.copy(id = "1:gondola_5", code = null, vehicleMode = "GONDOLA").toStopOrNull()
        assertEquals(TransportKind.CABLE_CAR, stop?.kind)
        assertNull(stop?.code)
    }

    @Test
    fun `lists drop null and invalid entries and keep order`() {
        val stops = listOf(dto, null, dto.copy(id = null), dto.copy(id = "1:969")).toStops()
        assertEquals(listOf("1:970", "1:969"), stops.map { it.id.value })
        assertEquals(emptyList(), null.toStops())
    }
}
