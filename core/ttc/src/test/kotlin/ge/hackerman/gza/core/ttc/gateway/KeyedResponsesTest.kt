package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.gateway.dto.toPolylineDtos
import ge.hackerman.gza.core.ttc.gateway.dto.toPositionDtos
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test

class KeyedResponsesTest {
    private fun obj(json: String) = TtcJson.decodeFromString<Map<String, JsonElement>>(json)

    @Test
    fun `an extra key of another shape drops only itself`() {
        val positions = obj(
            """{"timestamp":123,"0:01":[{"vehicleId":"1:981","lat":41.72,"lon":44.70}],"1:01":null}"""
        ).toPositionDtos()
        assertNull(positions["timestamp"])
        assertNull(positions["1:01"])
        assertEquals("1:981", positions["0:01"]?.single()?.vehicleId)
    }

    @Test
    fun `a polyline value of the wrong type is null`() {
        val polylines = obj("""{"0:01":{"encodedValue":"abc"},"1:01":[1,2]}""").toPolylineDtos()
        assertEquals("abc", polylines["0:01"]?.encodedValue)
        assertNull(polylines["1:01"])
    }
}
