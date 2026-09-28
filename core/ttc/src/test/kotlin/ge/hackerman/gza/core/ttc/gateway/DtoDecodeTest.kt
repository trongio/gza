package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.gateway.dto.BoardArrivalDto
import ge.hackerman.gza.core.ttc.gateway.dto.FeatureCollectionDto
import ge.hackerman.gza.core.ttc.gateway.dto.PatternStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.PlanResponseDto
import ge.hackerman.gza.core.ttc.gateway.dto.PolylineDto
import ge.hackerman.gza.core.ttc.gateway.dto.ProblemDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDetailDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.ServicePeriodDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.gateway.dto.VehiclePositionDto
import ge.hackerman.gza.core.ttc.testing.FixtureKind
import ge.hackerman.gza.core.ttc.testing.Fixtures
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DtoDecodeTest {
    @ParameterizedTest
    @MethodSource("jsonFixtures")
    fun `every recorded json fixture decodes into its dto`(path: String) {
        assertNotNull(decode(FixtureKind.of(path), Fixtures.text(path)))
    }

    @Test
    fun `all stops, 29 of them without a code`() {
        val stops = TtcJson.decodeFromString<List<StopDto?>>(Fixtures.text("stops/all-en.json"))
        assertEquals(2753, stops.size)
        assertEquals(29, stops.count { it?.code == null })
    }

    @Test
    fun `all routes`() {
        assertEquals(280, TtcJson.decodeFromString<List<RouteDto?>>(Fixtures.text("routes/all-en.json")).size)
    }

    @Test
    fun `551 has three service periods`() {
        val periods =
            TtcJson.decodeFromString<List<ServicePeriodDto?>>(Fixtures.text("schedule/551-0-01-en.json"))
        assertEquals(listOf("MONDAY", "SATURDAY", "SUNDAY"), periods.map { it?.fromDay })
    }

    @Test
    fun `472 uses 03 patterns`() {
        val route = TtcJson.decodeFromString<RouteDetailDto>(Fixtures.text("route/472-en.json"))
        assertEquals(listOf("0:03", "1:03"), route.patterns?.map { it?.patternSuffix })
        assertEquals("1:03", route.defaultPatternSuffix)
    }

    @Test
    fun `geocode properties keep their snake case names`() {
        val result =
            TtcJson.decodeFromString<FeatureCollectionDto>(Fixtures.text("reverse-geocode/1-970-en.json"))
        val properties = assertNotNull(result.features?.firstOrNull()?.properties)
        assertNotNull(properties.osmId)
        assertEquals("bus_stop", properties.osmValue)
    }

    @Test
    fun `every kind of fixture exists`() {
        val kinds = Fixtures.all().map(FixtureKind::of).toSet()
        assertTrue(kinds.containsAll(FixtureKind.entries), "missing ${FixtureKind.entries - kinds}")
    }

    companion object {
        @JvmStatic
        fun jsonFixtures(): List<String> = Fixtures.all().filter { it.endsWith(".json") }

        fun decode(kind: FixtureKind, text: String): Any = when (kind) {
            FixtureKind.STOPS -> TtcJson.decodeFromString<List<StopDto?>>(text)
            FixtureKind.STOP -> TtcJson.decodeFromString<StopDto>(text)
            FixtureKind.ROUTE_LIST -> TtcJson.decodeFromString<List<RouteDto?>>(text)
            FixtureKind.ROUTE_DETAIL -> TtcJson.decodeFromString<RouteDetailDto>(text)
            FixtureKind.BOARD -> TtcJson.decodeFromString<List<BoardArrivalDto?>>(text)
            FixtureKind.SCHEDULE -> TtcJson.decodeFromString<List<ServicePeriodDto?>>(text)
            FixtureKind.STOPS_OF_PATTERNS -> TtcJson.decodeFromString<List<PatternStopDto?>>(text)
            FixtureKind.POLYLINES -> TtcJson.decodeFromString<Map<String, PolylineDto?>>(text)
            FixtureKind.POSITIONS -> TtcJson.decodeFromString<Map<String, List<VehiclePositionDto?>?>>(text)
            FixtureKind.PLAN -> TtcJson.decodeFromString<PlanResponseDto>(text)
            FixtureKind.GEOCODE -> TtcJson.decodeFromString<FeatureCollectionDto>(text)
            FixtureKind.PROBLEM -> TtcJson.decodeFromString<ProblemDto>(text)
            FixtureKind.TEXT -> text
        }
    }
}
