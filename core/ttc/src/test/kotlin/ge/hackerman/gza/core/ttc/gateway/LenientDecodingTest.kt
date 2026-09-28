package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.gateway.dto.decodeEachElement
import ge.hackerman.gza.core.ttc.gateway.dto.decodeObject
import ge.hackerman.gza.core.ttc.gateway.dto.toFeatureCollectionDto
import ge.hackerman.gza.core.ttc.gateway.dto.toPlanResponseDto
import ge.hackerman.gza.core.ttc.gateway.dto.toPolylineDtos
import ge.hackerman.gza.core.ttc.gateway.dto.toPositionDtos
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test

class LenientDecodingTest {
    private fun json(text: String) = TtcJson.parseToJsonElement(text)

    @Test
    fun `an extra key of another shape drops only itself`() {
        val positions = json(
            """{"timestamp":123,"0:01":[{"vehicleId":"1:981","lat":41.72,"lon":44.70}],"1:01":null}"""
        ).toPositionDtos()
        assertNull(positions["timestamp"])
        assertNull(positions["1:01"])
        assertEquals("1:981", positions["0:01"]?.single()?.vehicleId)
    }

    @Test
    fun `a mistyped vehicle drops only itself within its pattern`() {
        val positions = json(
            """{"0:01":[{"vehicleId":"1:1","heading":"NaN"},null,{"vehicleId":"1:2","heading":90.0}]}"""
        ).toPositionDtos()
        assertEquals(listOf("1:2"), positions["0:01"]?.map { it.vehicleId })
    }

    @Test
    fun `a polyline value of the wrong type is null`() {
        val polylines = json("""{"0:01":{"encodedValue":"abc"},"1:01":[1,2]}""").toPolylineDtos()
        assertEquals("abc", polylines["0:01"]?.encodedValue)
        assertNull(polylines["1:01"])
    }

    @Test
    fun `list elements that do not fit, and nulls, drop only themselves`() {
        val routes = json(
            """[{"id":"1:R1","shortName":"1"},{"id":"1:R2","shortName":{"x":1}},null,7,{"id":"1:R3"}]"""
        ).decodeEachElement<RouteDto>()
        assertEquals(listOf("1:R1", "1:R3"), routes.map { it.id })
    }

    @Test
    fun `a body that is not a list is malformed`() {
        assertFailsWith<SerializationException> { json("""{"id":"1:970"}""").decodeEachElement<StopDto>() }
        assertFailsWith<SerializationException> { json("null").decodeEachElement<StopDto>() }
    }

    @Test
    fun `a body that is not an object is malformed`() {
        assertFailsWith<SerializationException> { json("[]").decodeObject<StopDto>() }
        assertFailsWith<SerializationException> { json("[]").toPositionDtos() }
        assertFailsWith<SerializationException> { json("\"x\"").toPlanResponseDto() }
        assertFailsWith<SerializationException> { json("1").toFeatureCollectionDto() }
    }

    @Test
    fun `the malformed message names the shape, never the body`() {
        val e =
            assertFailsWith<SerializationException> { json("""{"secret":"sentinel"}""").decodeEachElement<StopDto>() }
        assertFalse("sentinel" in e.message.orEmpty())
    }

    @Test
    fun `a mistyped itinerary drops only itself, the plan's places survive`() {
        val plan = json(
            """{"from":{"lat":41.7,"lon":44.7},"itineraries":[{"duration":"long"},{"duration":60},null]}"""
        ).toPlanResponseDto()
        assertEquals(41.7, plan.from?.lat)
        assertEquals(listOf(60L), plan.itineraries?.map { it?.duration })
    }

    @Test
    fun `a plan without itineraries keeps them null`() {
        assertNull(json("""{"itineraries":null}""").toPlanResponseDto().itineraries)
        assertNull(json("""{}""").toPlanResponseDto().itineraries)
    }

    @Test
    fun `itineraries of the wrong shape are malformed`() {
        assertFailsWith<SerializationException> { json("""{"itineraries":"none"}""").toPlanResponseDto() }
    }

    @Test
    fun `a mistyped geocode feature drops only itself`() {
        val features = json(
            """{"type":"FeatureCollection","features":[{"type":["x"]},{"type":"Feature"}]}"""
        ).toFeatureCollectionDto()
        assertEquals("FeatureCollection", features.type)
        assertEquals(listOf("Feature"), features.features?.map { it?.type })
    }
}
