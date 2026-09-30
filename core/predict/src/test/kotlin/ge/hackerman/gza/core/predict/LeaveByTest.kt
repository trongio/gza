package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.tbilisi
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class LeaveByTest {
    private val day = "2026-09-28"

    private fun departure(
        scheduled: String,
        predicted: String = scheduled,
        state: DepartureState = DepartureState.TimetableOnly,
        stop: String = "1:970"
    ) = PredictedDeparture(
        routeId = RouteId("1:R97493"),
        routeShortName = "326",
        kind = TransportKind.BUS,
        pattern = PatternSuffix("0:01"),
        headsign = null,
        stopId = StopId(stop),
        serviceDate = tbilisi("${day}T00:00:00").toLocalDate(),
        scheduled = tbilisi("${day}T$scheduled"),
        predicted = tbilisi("${day}T$predicted"),
        state = state
    )

    private fun option(time: String, walk: Int, stop: String = "1:970"): LeaveOption {
        val departure = departure(time, stop = stop)
        return LeaveOption(departure, departure.leaveBy(walk))
    }

    @Test
    fun `leave by is the departure minus walk and buffer`() {
        assertEquals(tbilisi("${day}T17:07:00"), departure("17:13:00").leaveBy(walkMinutes = 5, bufferMinutes = 1))
    }

    @Test
    fun `buffer defaults to 1 minute`() {
        assertEquals(tbilisi("${day}T17:07:00"), departure("17:13:00").leaveBy(walkMinutes = 5))
    }

    @Test
    fun `out of range walk and buffer are clamped`() {
        val at1713 = departure("17:13:00")
        assertEquals(tbilisi("${day}T17:12:00"), at1713.leaveBy(walkMinutes = -3))
        assertEquals(tbilisi("${day}T17:10:00"), at1713.leaveBy(walkMinutes = 0, bufferMinutes = 9))
        assertEquals(tbilisi("${day}T17:13:00"), at1713.leaveBy(walkMinutes = 0, bufferMinutes = -1))
        assertEquals(tbilisi("${day}T15:12:00"), at1713.leaveBy(walkMinutes = 500))
    }

    @Test
    fun `a waiting bus counts from when it leaves`() {
        val waiting = departure(
            "17:49:00",
            predicted = "17:50:30",
            state = DepartureState.Waiting(VehicleId("1:3046"), tbilisi("${day}T17:50:30"))
        )
        assertEquals(tbilisi("${day}T17:44:30"), waiting.leaveBy(walkMinutes = 5))
    }

    @Test
    fun `best is the earliest catchable option and fallback the next`() {
        val now = tbilisi("${day}T17:00:00")
        val options = listOf(option("17:31:00", 5), option("16:59:00", 0), option("17:13:00", 5))
        val plan = options.planFrom(now)
        assertEquals(tbilisi("${day}T17:07:00"), plan.best?.leaveBy)
        assertEquals(tbilisi("${day}T17:25:00"), plan.fallback?.leaveBy)
    }

    @Test
    fun `leaving right now still counts as catchable`() {
        val now = tbilisi("${day}T17:07:00")
        assertEquals(tbilisi("${day}T17:07:00"), listOf(option("17:13:00", 5)).planFrom(now).best?.leaveBy)
    }

    @Test
    fun `nothing catchable gives an empty plan`() {
        val plan = listOf(option("17:13:00", 5)).planFrom(tbilisi("${day}T17:07:01"))
        assertNull(plan.best)
        assertNull(plan.fallback)
        assertEquals(LeavePlan(null, null), emptyList<LeaveOption>().planFrom(tbilisi("${day}T17:00:00")))
    }

    @Test
    fun `one option has no fallback`() {
        val plan = listOf(option("17:13:00", 5)).planFrom(tbilisi("${day}T17:00:00"))
        assertEquals(tbilisi("${day}T17:13:00"), plan.best?.departure?.scheduled)
        assertNull(plan.fallback)
    }

    @Test
    fun `options from two stops order by leave by, not by departure`() {
        val far = option("17:20:00", walk = 15, stop = "1:969")
        val near = option("17:22:00", walk = 2, stop = "1:970")
        val plan = listOf(near, far).planFrom(tbilisi("${day}T17:00:00"))
        assertEquals(far, plan.best)
        assertEquals(near, plan.fallback)
    }

    @Test
    fun `equal leave by prefers the earlier departure`() {
        val early = option("17:10:00", walk = 3)
        val late = option("17:12:00", walk = 5)
        assertEquals(early, listOf(late, early).planFrom(tbilisi("${day}T17:00:00")).best)
    }
}
