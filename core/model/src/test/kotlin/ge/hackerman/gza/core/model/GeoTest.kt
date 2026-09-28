package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GeoTest {
    private val stop970 = LatLon(41.722055, 44.703114)
    private val freedomSquare = LatLon(41.694033, 44.801559)

    @Test
    fun `ofOrNull rejects missing and impossible coordinates`() {
        assertNull(LatLon.ofOrNull(null, 44.7))
        assertNull(LatLon.ofOrNull(41.7, null))
        assertNull(LatLon.ofOrNull(Double.NaN, 44.7))
        assertNull(LatLon.ofOrNull(41.7, Double.POSITIVE_INFINITY))
        assertNull(LatLon.ofOrNull(91.0, 44.7))
        assertNull(LatLon.ofOrNull(41.7, -181.0))
        assertEquals(stop970, LatLon.ofOrNull(41.722055, 44.703114))
    }

    @Test
    fun `constructor rejects impossible coordinates`() {
        assertFailsWith<IllegalArgumentException> { LatLon(91.0, 0.0) }
    }

    @Test
    fun `tbilisi bounds contain the city and not null island`() {
        assertTrue(stop970 in TBILISI_BOUNDS)
        assertTrue(freedomSquare in TBILISI_BOUNDS)
        assertFalse(LatLon(0.0, 0.0) in TBILISI_BOUNDS)
    }

    @Test
    fun `bounding box keeps the gateway order`() {
        assertEquals(44.6, TBILISI_BOUNDS.minLon)
        assertEquals(41.6, TBILISI_BOUNDS.minLat)
        assertEquals(45.0, TBILISI_BOUNDS.maxLon)
        assertEquals(41.85, TBILISI_BOUNDS.maxLat)
        assertFailsWith<IllegalArgumentException> { BoundingBox(45.0, 41.6, 44.6, 41.85) }
    }
}
