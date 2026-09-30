package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.gateway.dto.PolylineDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.testing.Fixtures
import ge.hackerman.gza.core.ttc.testing.distanceMeters
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PolylineMappersTest {
    @Test
    fun `invalid keys and blank values are dropped and null colour is kept`() {
        val polylines = mapOf(
            "0:01" to PolylineDto(null, "_p~iF~ps|U"),
            "bad" to PolylineDto("00B38B", "_p~iF~ps|U"),
            "1:01" to PolylineDto("00B38B", " "),
            "1:02" to null
        ).toRoutePolylines()
        assertEquals(listOf("0:01"), polylines.map { it.pattern.value })
        assertNull(polylines.single().color)
    }

    @Test
    fun `the recorded 326 shape runs from terminus to terminus`() {
        val dtos = TtcJson.decodeFromString<Map<String, PolylineDto?>>(Fixtures.text("polylines/326.json"))
        val outbound = dtos.toRoutePolylines().single { it.pattern.value == "0:01" }
        val points = outbound.encoded.decode()
        assertEquals(356, points.size)
        assertTrue(distanceMeters(points.first(), stopLocation("1:970")) < 20)
        assertTrue(distanceMeters(points.last(), stopLocation("1:824")) < 20)
    }

    private fun stopLocation(id: String): LatLon {
        val stops = TtcJson.decodeFromString<List<StopDto?>>(Fixtures.text("stops/all-en.json"))
        return requireNotNull(stops.firstNotNullOf { dto -> dto?.takeIf { it.id == id }?.toStopOrNull() }).location
    }
}
