package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class TripRequestTest {
    @Test
    fun `defaults leave now on every transit kind by the quickest way`() {
        val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.694033, 44.801559))
        assertEquals(TripTime.LeaveNow, request.time)
        assertEquals(TripOptimize.QUICK, request.optimize)
        assertEquals(
            setOf(TransportKind.BUS, TransportKind.MINIBUS, TransportKind.METRO, TransportKind.CABLE_CAR),
            request.kinds
        )
        assertFalse(TransportKind.UNKNOWN in request.kinds)
    }
}
