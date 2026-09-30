package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.ttc.gateway.dto.PatternDto
import ge.hackerman.gza.core.ttc.gateway.dto.PatternStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDetailDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopRefDto
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class RouteMappersTest {
    private val route = RouteDto("1:R97493", "326", "Baratashvili St - Politkovskaya St", "00B38B", "BUS")

    @Test
    fun `a minibus route id makes a minibus`() {
        assertEquals(TransportKind.MINIBUS, route.copy(id = "1:minibusR24579").toRouteOrNull()?.kind)
        assertEquals(TransportKind.BUS, route.toRouteOrNull()?.kind)
    }

    @Test
    fun `subway is metro and tram is unknown`() {
        assertEquals(TransportKind.METRO, route.copy(mode = "SUBWAY").toRouteOrNull()?.kind)
        assertEquals(TransportKind.UNKNOWN, route.copy(mode = "TRAM").toRouteOrNull()?.kind)
    }

    @Test
    fun `colour parses case insensitively and bad colours are null`() {
        assertEquals(0xFF505B, route.copy(color = "ff505b").toRouteOrNull()?.color?.rgb)
        assertNull(route.copy(color = "red").toRouteOrNull()?.color)
    }

    @Test
    fun `invalid id drops the route and missing short name falls back`() {
        assertNull(route.copy(id = "R97493").toRouteOrNull())
        assertEquals("R97493", route.copy(shortName = null).toRouteOrNull()?.shortName)
        assertNull(route.copy(longName = "").toRouteOrNull()?.longName)
    }

    @Test
    fun `route detail drops patterns with an invalid suffix`() {
        val detail = RouteDetailDto(
            id = "1:minibusR25521",
            shortName = "472",
            longName = null,
            color = "0033B4",
            mode = "BUS",
            patterns = listOf(
                PatternDto("0:03", 0, StopRefDto("1:2931", "Ekimi Lan"), StopRefDto("1:977", null), "headsign"),
                PatternDto("0-03"),
                null,
                PatternDto("1:03", null, StopRefDto("bad"), null, null)
            ),
            defaultPatternSuffix = "1:03"
        ).toRouteDetailOrNull()
        requireNotNull(detail)
        assertEquals(listOf("0:03", "1:03"), detail.patterns.map { it.suffix.value })
        assertEquals(StopId("1:2931"), detail.patterns[0].firstStop?.id)
        assertNull(detail.patterns[1].firstStop)
        assertEquals(1, detail.patterns[1].directionId, "falls back to the suffix")
        assertEquals(PatternSuffix("1:03"), detail.defaultPattern)
        assertEquals(TransportKind.MINIBUS, detail.kind)
    }

    @Test
    fun `route detail without a valid id is null and a bad default is null`() {
        assertNull(RouteDetailDto(id = null).toRouteDetailOrNull())
        assertNull(RouteDetailDto(id = "1:R1", defaultPatternSuffix = "x").toRouteDetailOrNull()?.defaultPattern)
    }

    @Test
    fun `pattern stops keep order and duplicates and drop other patterns`() {
        fun stop(id: String) = StopDto(id, null, id, 41.7, 44.7, "BUS")
        val stops = listOf(
            PatternStopDto(stop("1:970"), listOf("0:01")),
            PatternStopDto(stop("1:1"), listOf("1:01")),
            PatternStopDto(stop("1:2"), null),
            PatternStopDto(stop("1:970"), listOf("0:01", "1:01")),
            PatternStopDto(null, listOf("0:01")),
            null
        ).toPatternStops(RouteId("1:R97493"), PatternSuffix("0:01"))
        assertEquals(listOf("1:970", "1:2", "1:970"), stops.stops.map { it.id.value })
    }
}
