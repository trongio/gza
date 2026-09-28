package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RouteTest {
    @Test
    fun `a minibus route is recognised from its id`() {
        val route =
            Route(RouteId("1:minibusR24579"), "551", null, RouteColor.ofHexOrNull("0033B4"), TransportKind.MINIBUS)
        assertTrue(route.id.isMinibus)
        assertFalse(route.copy(id = RouteId("1:R97493")).id.isMinibus)
    }

    @Test
    fun `pattern direction comes from its suffix`() {
        val pattern = Pattern(PatternSuffix("1:03"), 1, StopRef(StopId("1:970"), null), null, "Lobzhanidze St")
        assertEquals(pattern.directionId, pattern.suffix.directionId)
    }
}
