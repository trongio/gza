package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RouteColorTest {
    @Test
    fun `hex colours parse case insensitively with or without hash`() {
        assertEquals(0x00B38B, RouteColor.ofHexOrNull("00B38B")?.rgb)
        assertEquals(0xFF505B, RouteColor.ofHexOrNull("ff505b")?.rgb)
        assertEquals(0x0033B4, RouteColor.ofHexOrNull("#0033B4")?.rgb)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0033B", "zzzzzz", "", "#", "0033B44", "-033B4"])
    fun `anything else is null`(value: String) {
        assertNull(RouteColor.ofHexOrNull(value))
    }

    @Test
    fun `null is null`() {
        assertNull(RouteColor.ofHexOrNull(null))
    }
}
