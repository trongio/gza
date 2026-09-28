package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
}
