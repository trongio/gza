package ge.hackerman.gza.core.predict.testing

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_301
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_326
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_551
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class PredictFixturesTest {
    private val stop970 = StopId("1:970")

    @Test
    fun `326 derived case has the parked bus with no heading and no next stop`() {
        val positions = PredictFixtures.positions("terminus/326-derived-1732/positions.json", ROUTE_326)
        val parked = positions.vehicles.single { it.vehicleId == VehicleId("1:3046") }
        assertEquals(PatternSuffix("0:01"), parked.pattern)
        assertNull(parked.headingDegrees)
        assertNull(parked.nextStopId)
        assertEquals(Instant.parse("2026-09-28T13:32:00Z"), positions.fetchedAt)
    }

    @Test
    fun `first 326 time at 1 970 is 07 55`() {
        val schedule = PredictFixtures.schedule("terminus/326-derived-1732/schedule-0-01.json", ROUTE_326, "0:01")
        val weekday = schedule.periods.first()
        val first = weekday.stops.single { it.stopId == stop970 }
        assertEquals(1, first.position)
        assertEquals(ServiceMinute.parseOrNull("7:55"), first.times.first())
    }

    @Test
    fun `board reads the 0 minute hint of each terminus case`() {
        val cases = mapOf(
            "terminus/326-derived-1732/arrival-times.json" to "326",
            "terminus/551-20260928T2043/arrival-times.json" to "551",
            "terminus/301-20260928T2102/arrival-times.json" to "301"
        )
        cases.forEach { (path, route) ->
            val board = PredictFixtures.board(path, stop970)
            assertEquals(0, board.arrivals.first { it.routeShortName == route }.realtimeMinutesHint, path)
        }
    }

    @Test
    fun `positions of the real terminus cases load`() {
        val p551 = PredictFixtures.positions("terminus/551-20260928T2043/positions.json", ROUTE_551)
        val p301 = PredictFixtures.positions("terminus/301-20260928T2102/positions.json", ROUTE_301)
        assertEquals(Instant.parse("2026-09-28T16:44:48Z"), p551.fetchedAt)
        assertEquals(true, p301.vehicles.isNotEmpty())
    }

    @Test
    fun `stop route and patterns load`() {
        val stop = PredictFixtures.stop("stop/1-970-en.json")
        assertEquals(stop970, stop.id)
        assertEquals(41.722055, stop.location.lat)
        assertEquals(TransportKind.BUS, stop.kind)
        assertEquals(
            "Baratashvili St",
            PredictFixtures.routeDetailPatterns("route/326-en.json").single {
                it.suffix == PatternSuffix("0:01")
            }.headsign
        )
        assertEquals(StopId("1:20225"), PredictFixtures.patternStops("stops-of-patterns/551-1-01-en.json").first().id)
        assertEquals(TransportKind.MINIBUS, PredictFixtures.route("route/551-en.json").kind)
    }

    @Test
    fun `test clocks sit in utc at the given Tbilisi time`() {
        val clock = tbilisiClock("2026-09-28T17:32:00")
        assertEquals(ZoneOffset.UTC, clock.zone)
        assertEquals(Instant.parse("2026-09-28T13:32:00Z"), clock.instant())
    }
}
