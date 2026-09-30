package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.Synthetic.home
import ge.hackerman.gza.core.predict.testing.Synthetic.parked
import ge.hackerman.gza.core.predict.testing.Synthetic.positions
import ge.hackerman.gza.core.predict.testing.Synthetic.route
import ge.hackerman.gza.core.predict.testing.Synthetic.schedule
import ge.hackerman.gza.core.predict.testing.at
import ge.hackerman.gza.core.predict.testing.tbilisi
import ge.hackerman.gza.core.predict.testing.tbilisiClock
import java.time.DayOfWeek.MONDAY
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Leave-by when you should already have left, and walks longer than the wait. */
class LeaveByEdgeCaseTest {
    private val r326 = route("326")
    private val day = "2026-09-28"

    // Mondays only, so tomorrow adds no later bus to catch.
    private fun rows(now: String, vararg times: String): List<PredictedDeparture> =
        DeparturePredictor(tbilisiClock("${day}T$now")).predict(
            StopRequest(
                home,
                listOf(RouteSnapshot(r326, listOf(schedule(r326, "0:01", times.toList(), from = MONDAY, to = MONDAY))))
            )
        ).departures

    private fun options(rows: List<PredictedDeparture>, walk: Int, buffer: Int = DEFAULT_BUFFER_MINUTES) =
        rows.map { LeaveOption(it, it.leaveBy(walk, buffer)) }

    @Test
    fun `leave by already past is negative and the plan skips that bus`() {
        val now = tbilisi("${day}T17:45:00")
        val rows = rows("17:45:00", "17:49", "18:07")
        val at1749 = rows.at("326", "17:49")
        val leaveBy = at1749.leaveBy(walkMinutes = 10)
        assertEquals(tbilisi("${day}T17:38:00"), leaveBy)
        assertTrue(leaveBy.isBefore(now), "should have left 7 min ago")

        val plan = options(rows, walk = 10).planFrom(now)
        assertEquals(tbilisi("${day}T18:07:00"), plan.best?.departure?.scheduled)
        assertEquals(tbilisi("${day}T17:56:00"), plan.best?.leaveBy)
        assertFalse(plan.best!!.leaveBy.isBefore(now))
    }

    @Test
    fun `a walk longer than the time to every departure leaves nothing to catch`() {
        val now = tbilisi("${day}T17:45:00")
        val plan = options(rows("17:45:00", "17:49", "17:55"), walk = 30).planFrom(now)
        assertNull(plan.best)
        assertNull(plan.fallback)
    }

    @Test
    fun `walk plus buffer exactly equal to the wait is still catchable, one minute more is not`() {
        val now = tbilisi("${day}T17:39:00")
        val only = rows("17:39:00", "17:49")
        assertEquals(now, options(only, walk = 9, buffer = 1).planFrom(now).best?.leaveBy)
        assertNull(options(only, walk = 9, buffer = 2).planFrom(now).best)
    }

    @Test
    fun `a row inside the past grace is listed but never the plan`() {
        val now = tbilisi("${day}T17:49:30")
        val rows = rows("17:49:30", "17:49", "18:07")
        assertEquals(tbilisi("${day}T17:49:00"), rows.first().scheduled)
        val plan = options(rows, walk = 0, buffer = 0).planFrom(now)
        assertEquals(tbilisi("${day}T18:07:00"), plan.best?.departure?.scheduled)
        assertNull(plan.fallback)
    }

    @Test
    fun `a late bus you can still reach is the plan, from its moving time`() {
        val memory = LayoverMemory(
            mapOf(
                VehicleId("1:3046") to ParkedVehicle(
                    r326.id,
                    PatternSuffix("0:01"),
                    tbilisi("${day}T17:40:00").toInstant(),
                    tbilisi("${day}T17:49:00")
                )
            )
        )
        val nowText = "${day}T17:52:00"
        val now = tbilisi(nowText)
        val snapshot = RouteSnapshot(
            r326,
            listOf(schedule(r326, "0:01", listOf("17:49", "18:07"))),
            positions = positions(r326, now.toInstant(), parked("1:3046"))
        )
        val rows = DeparturePredictor(
            tbilisiClock(nowText)
        ).predict(StopRequest(home, listOf(snapshot), memory = memory))
            .departures
        val late = rows.at("326", "17:49")
        assertTrue(late.state is DepartureState.Late)
        assertEquals(tbilisi("${day}T17:53:00"), late.predicted)
        // 1 min away with a 0 walk and no buffer: catchable exactly now.
        assertEquals(now, late.leaveBy(walkMinutes = 0, bufferMinutes = 1))
        assertEquals(late, options(rows, walk = 0, buffer = 1).planFrom(now).best?.departure)
        // Any walk at all and the late bus is gone; the plan falls to 18:07.
        assertEquals(
            tbilisi("${day}T18:07:00"),
            options(rows, walk = 1, buffer = 1).planFrom(now).best?.departure?.scheduled
        )
    }

    @Test
    fun `leave by is never after the departure, whatever is stored`() {
        rows("06:00:00", "17:49").forEach { row ->
            listOf(Int.MIN_VALUE, -1, 0, 1, 119, 120, 121, Int.MAX_VALUE).forEach { walk ->
                listOf(Int.MIN_VALUE, -1, 0, 3, 4, Int.MAX_VALUE).forEach { buffer ->
                    val leaveBy = row.leaveBy(walk, buffer)
                    assertFalse(leaveBy.isAfter(row.predicted), "walk $walk buffer $buffer")
                    assertFalse(
                        leaveBy.isBefore(row.predicted.minusMinutes(MAX_WALK_MINUTES + MAX_BUFFER_MINUTES.toLong())),
                        "walk $walk buffer $buffer"
                    )
                }
            }
        }
    }
}
