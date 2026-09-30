package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.TransportKind
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class KindMappingTest {
    private val minibus = RouteId("1:minibusR24579")
    private val bus = RouteId("1:R97493")

    @Test
    fun `bus is a minibus only for a minibus route id`() {
        assertEquals(TransportKind.MINIBUS, KindMapping.fromMode("BUS", minibus))
        assertEquals(TransportKind.BUS, KindMapping.fromMode("BUS", bus))
        assertEquals(TransportKind.BUS, KindMapping.fromMode("BUS", null))
    }

    @Test
    fun `subway and gondola`() {
        assertEquals(TransportKind.METRO, KindMapping.fromMode("SUBWAY", null))
        assertEquals(TransportKind.CABLE_CAR, KindMapping.fromMode("GONDOLA", null))
    }

    @Test
    fun `case and whitespace do not matter`() {
        assertEquals(TransportKind.METRO, KindMapping.fromMode(" subway ", null))
    }

    @Test
    fun `unknown and null modes are unknown`() {
        assertEquals(TransportKind.UNKNOWN, KindMapping.fromMode("TRAM", null))
        assertEquals(TransportKind.UNKNOWN, KindMapping.fromMode(null, minibus))
        assertEquals(TransportKind.UNKNOWN, KindMapping.fromMode("", null))
    }
}
