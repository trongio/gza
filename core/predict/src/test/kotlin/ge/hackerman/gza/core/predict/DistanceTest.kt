package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DistanceTest {
    private val stop970 = LatLon(41.722055, 44.703114)
    private val stop969 = LatLon(41.721692, 44.704641)
    private val stop824 = LatLon(41.696663, 44.804746)

    @Test
    fun `same point is zero`() {
        assertEquals(0.0, stop970.metersTo(stop970))
    }

    @Test
    fun `neighbouring stops are about 133 m apart`() {
        val meters = stop970.metersTo(stop969)
        assertTrue(abs(meters - 133) <= 2, "$meters")
    }

    @Test
    fun `distance is symmetric`() {
        assertEquals(stop970.metersTo(stop824), stop824.metersTo(stop970), 1e-9)
    }

    @Test
    fun `the two ends of 326 are about 8 9 km apart`() {
        val meters = stop970.metersTo(stop824)
        assertTrue(abs(meters - 8_900) <= 89, "$meters")
    }
}
