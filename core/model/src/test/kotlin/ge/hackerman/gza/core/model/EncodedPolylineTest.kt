package ge.hackerman.gza.core.model

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EncodedPolylineTest {
    // The worked example from Google's "Encoded Polyline Algorithm Format" page.
    private val google = EncodedPolyline("""_p~iF~ps|U_ulLnnqC_mqNvxq`@""")

    @Test
    fun `the reference example decodes`() {
        val points = google.decode()
        assertEquals(3, points.size)
        assertNear(LatLon(38.5, -120.2), points[0])
        assertNear(LatLon(40.7, -120.95), points[1])
        assertNear(LatLon(43.252, -126.453), points[2])
    }

    @Test
    fun `empty string is no points`() {
        assertTrue(EncodedPolyline("").decode().isEmpty())
    }

    @Test
    fun `truncated input keeps the whole points before the cut`() {
        val cut = google.value.dropLast(3)
        val points = EncodedPolyline(cut).decode()
        assertEquals(2, points.size)
        assertNear(LatLon(40.7, -120.95), points[1])
    }

    @Test
    fun `half a point is dropped`() {
        // Only the latitude of the first point.
        assertTrue(EncodedPolyline("_p~iF").decode().isEmpty())
    }

    @Test
    fun `characters outside the alphabet stop decoding`() {
        val points = EncodedPolyline(google.value.substring(0, 10) + " garbage").decode()
        assertEquals(1, points.size)
        assertNear(LatLon(38.5, -120.2), points[0])
    }

    @Test
    fun `an endless continuation does not overflow`() {
        assertTrue(EncodedPolyline("~".repeat(50)).decode().isEmpty())
    }

    private fun assertNear(expected: LatLon, actual: LatLon) {
        assertTrue(abs(expected.lat - actual.lat) < 1e-6 && abs(expected.lon - actual.lon) < 1e-6, "$actual")
    }
}
