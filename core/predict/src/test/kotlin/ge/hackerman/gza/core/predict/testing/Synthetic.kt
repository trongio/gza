package ge.hackerman.gza.core.predict.testing

import ge.hackerman.gza.core.model.BoardArrival
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import java.time.DayOfWeek
import java.time.Instant

/** Small hand-made inputs around stop 1:970, for rules the fixtures do not isolate. */
object Synthetic {
    val HOME = LatLon(41.722055, 44.703114)
    val home = Stop(StopId("1:970"), "970", "Ana Politkovskaia Street", HOME, TransportKind.BUS)

    fun route(number: String, id: String = "1:R$number", kind: TransportKind = TransportKind.BUS) =
        Route(RouteId(id), number, null, null, kind)

    fun minute(raw: String): ServiceMinute = checkNotNull(ServiceMinute.parseOrNull(raw))

    /**
     * A pattern that runs every day: [times] at [stopId] listed at [position], plus a filler
     * stop before and after so the position means what the test says.
     */
    fun schedule(
        route: Route,
        pattern: String,
        times: List<String>,
        stopId: StopId = home.id,
        position: Int = 1,
        from: DayOfWeek = DayOfWeek.MONDAY,
        to: DayOfWeek = DayOfWeek.SUNDAY
    ): RouteSchedule {
        val stops = (1..maxOf(position + 1, 2)).map { at ->
            val id = if (at == position) stopId else StopId("9:$at")
            ScheduledStop(id, null, at, times.map(::minute))
        }
        return RouteSchedule(route.id, PatternSuffix(pattern), listOf(ServicePeriod(from, to, emptySet(), stops)))
    }

    fun parked(id: String, pattern: String = "0:01", at: LatLon = HOME) =
        VehiclePosition(VehicleId(id), PatternSuffix(pattern), at, null, null)

    fun moving(id: String, pattern: String = "0:01", at: LatLon = HOME) =
        VehiclePosition(VehicleId(id), PatternSuffix(pattern), at, 90.0, StopId("1:969"))

    fun positions(route: Route, fetchedAt: Instant, vararg vehicles: VehiclePosition) =
        RoutePositions(route.id, fetchedAt, vehicles.toList())

    fun boardRow(
        number: String,
        pattern: String?,
        realtime: Int?,
        scheduled: Int? = null,
        kind: TransportKind = TransportKind.BUS
    ) = BoardArrival(number, pattern?.let(::PatternSuffix), null, null, kind, realtime != null, realtime, scheduled)
}
