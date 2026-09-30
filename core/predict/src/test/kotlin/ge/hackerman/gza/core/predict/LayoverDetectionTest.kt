package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class LayoverDetectionTest {
    private val terminus = LatLon(41.722055, 44.703114)

    /** A fix [meters] due north of the terminus. */
    private fun vehicle(meters: Double, heading: Double? = null, nextStop: String? = null) = VehiclePosition(
        vehicleId = VehicleId("1:3046"),
        pattern = PatternSuffix("0:01"),
        location = LatLon(terminus.lat + meters / 111_320.0, terminus.lon),
        headingDegrees = heading,
        nextStopId = nextStop?.let(::StopId)
    )

    @Test
    fun `no heading and no next stop within 500 m is a layover`() {
        listOf(0.0, 6.0, 318.0, 442.0, 499.999).forEach {
            assertTrue(vehicle(it).isInLayoverAt(terminus), "$it m")
        }
    }

    @Test
    fun `the radius itself is inside and 501 m is not`() {
        assertTrue(vehicle(0.0).isInLayoverAt(terminus, PredictionRules(layoverRadiusMeters = 0.0)))
        assertFalse(vehicle(501.0).isInLayoverAt(terminus))
    }

    @Test
    fun `a heading means the bus is moving`() {
        assertFalse(vehicle(76.0, heading = 98.9, nextStop = "1:969").isInLayoverAt(terminus))
    }

    @Test
    fun `a heading with no next stop is a bus pulling in`() {
        assertFalse(vehicle(22.0, heading = 180.0).isInLayoverAt(terminus))
        assertFalse(vehicle(22.0, heading = 180.0).isBetweenTrips())
    }

    @Test
    fun `a next stop without a heading is not a layover either`() {
        assertFalse(vehicle(0.0, nextStop = "1:969").isInLayoverAt(terminus))
    }
}
