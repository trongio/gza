package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IdsTest {
    @Test
    fun `stop id splits into feed and local id`() {
        val id = StopId("1:970")
        assertEquals("1", id.feedId)
        assertEquals("970", id.localId)
    }

    @Test
    fun `minibus route keeps its full local id`() {
        val id = RouteId("1:minibusR24579")
        assertEquals("1", id.feedId)
        assertEquals("minibusR24579", id.localId)
    }

    @Test
    fun `metro id keeps underscores`() {
        assertEquals("Metro_Metro_1", StopId("1:Metro_Metro_1").localId)
    }

    @Test
    fun `ids with the same value are equal`() {
        assertEquals(StopId("1:970"), StopId("1:970"))
        assertNotEquals(StopId("1:970"), StopId("1:971"))
        assertEquals(RouteId("1:R97493"), RouteId("1:R97493"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "970", ":970", "1:"])
    fun `invalid stop ids are rejected`(value: String) {
        assertFailsWith<IllegalArgumentException> { StopId(value) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "R97493", ":R97493"])
    fun `invalid route ids are rejected`(value: String) {
        assertFailsWith<IllegalArgumentException> { RouteId(value) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0:01", "1:03", "12:10"])
    fun `pattern suffixes are accepted`(value: String) {
        assertEquals(value, PatternSuffix(value).value)
        assertEquals(value, PatternSuffix.ofOrNull(value)?.value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0-01", ":01", "a:b", "", " ", "0:", "0:01 ", "99999999999:01"])
    fun `invalid pattern suffixes are rejected`(value: String) {
        assertFailsWith<IllegalArgumentException> { PatternSuffix(value) }
        assertNull(PatternSuffix.ofOrNull(value))
    }

    @Test
    fun `pattern suffix knows its direction`() {
        assertEquals(0, PatternSuffix("0:01").directionId)
        assertEquals(1, PatternSuffix("1:03").directionId)
    }

    @Test
    fun `vehicle id is feed prefixed`() {
        assertEquals("1:981", VehicleId("1:981").value)
        assertFailsWith<IllegalArgumentException> { VehicleId("981") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "970", ":970", "1:"])
    fun `ofOrNull returns null instead of throwing`(value: String) {
        assertNull(StopId.ofOrNull(value))
        assertNull(RouteId.ofOrNull(value))
        assertNull(VehicleId.ofOrNull(value))
    }

    @Test
    fun `ofOrNull accepts null and valid ids`() {
        assertNull(StopId.ofOrNull(null))
        assertNull(RouteId.ofOrNull(null))
        assertNull(VehicleId.ofOrNull(null))
        assertNull(PatternSuffix.ofOrNull(null))
        assertEquals(StopId("1:970"), StopId.ofOrNull("1:970"))
        assertEquals(RouteId("1:R97493"), RouteId.ofOrNull("1:R97493"))
        assertEquals(VehicleId("1:981"), VehicleId.ofOrNull("1:981"))
    }

    @Test
    fun `minibus routes are recognised by id only`() {
        assertTrue(RouteId("1:minibusR24579").isMinibus)
        assertTrue(RouteId("1:MinibusR1").isMinibus)
        assertFalse(RouteId("1:R97493").isMinibus)
        assertFalse(RouteId("1:Metro_Metro_1").isMinibus)
    }
}
