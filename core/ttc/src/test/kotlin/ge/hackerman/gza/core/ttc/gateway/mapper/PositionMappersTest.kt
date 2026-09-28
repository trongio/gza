package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.dto.VehiclePositionDto
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class PositionMappersTest {
    private val at = Instant.parse("2026-09-28T16:44:48Z")
    private val parked = VehiclePositionDto("1:981", 41.7220535, 44.7031136, null, null)
    private val moving = VehiclePositionDto("1:984", 41.7385254, 44.7810783, 157.49, "1:4592")

    private fun map(positions: Map<String, List<VehiclePositionDto?>?>) =
        positions.toRoutePositions(RouteId("1:minibusR24579"), at)

    @Test
    fun `an invalid pattern key drops its group`() {
        val result = map(mapOf("0:01" to listOf(parked), "bad" to listOf(moving)))
        assertEquals(listOf("1:981"), result.vehicles.map { it.vehicleId.value })
    }

    @Test
    fun `null heading and null next stop are kept`() {
        val vehicle = map(mapOf("0:01" to listOf(parked))).vehicles.single()
        assertNull(vehicle.headingDegrees)
        assertNull(vehicle.nextStopId)
    }

    @Test
    fun `heading without a next stop is kept`() {
        val vehicle = map(mapOf("0:01" to listOf(moving.copy(nextStopId = null)))).vehicles.single()
        assertEquals(157.49, vehicle.headingDegrees)
        assertNull(vehicle.nextStopId)
    }

    @Test
    fun `an invalid next stop id becomes null`() {
        assertNull(map(mapOf("1:01" to listOf(moving.copy(nextStopId = "bad")))).vehicles.single().nextStopId)
        assertEquals(StopId("1:4592"), map(mapOf("1:01" to listOf(moving))).vehicles.single().nextStopId)
    }

    @Test
    fun `vehicles without id or position are dropped and null groups are empty`() {
        val result = map(
            mapOf(
                "0:01" to listOf(parked.copy(vehicleId = null), parked.copy(lat = null), null, moving),
                "1:01" to null
            )
        )
        assertEquals(listOf("1:984"), result.vehicles.map { it.vehicleId.value })
        assertEquals(at, result.fetchedAt)
    }

    @Test
    fun `empty response is no vehicles`() {
        assertEquals(emptyList(), null.toRoutePositions(RouteId("1:R1"), at).vehicles)
    }
}
