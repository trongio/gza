package ge.hackerman.gza.core.predict.testing

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.predict.DepartureState
import ge.hackerman.gza.core.predict.PredictedDeparture
import ge.hackerman.gza.core.predict.RouteSnapshot
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_301
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_326
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_472
import ge.hackerman.gza.core.predict.testing.PredictFixtures.ROUTE_551

/** Real routes, schedules and stops from the fixtures, by route number. */
object Snapshots {
    private val ids = mapOf("301" to ROUTE_301, "326" to ROUTE_326, "551" to ROUTE_551, "472" to ROUTE_472)

    val stop970: Stop by lazy { PredictFixtures.stop("stop/1-970-en.json") }

    fun routeId(number: String): RouteId = ids.getValue(number)

    /** `schedule/<number>-<pattern>-en.json`, pattern written as `0-01`. */
    fun schedule(number: String, pattern: String): RouteSchedule =
        PredictFixtures.schedule("schedule/$number-$pattern-en.json", routeId(number), pattern.replace('-', ':'))

    fun positions(path: String, number: String): RoutePositions = PredictFixtures.positions(path, routeId(number))

    /** The first stop of a pattern, from `stops-of-patterns`. */
    fun firstStop(number: String, pattern: String): Stop =
        PredictFixtures.patternStops("stops-of-patterns/$number-$pattern-en.json").first()

    fun snapshot(number: String, schedules: List<RouteSchedule>, positions: RoutePositions? = null) = RouteSnapshot(
        route = PredictFixtures.route("route/$number-en.json"),
        schedules = schedules,
        patterns = PredictFixtures.routeDetailPatterns("route/$number-en.json"),
        positions = positions
    )
}

/** The earliest listed row of [number] scheduled at local [time] (`17:49`). */
fun List<PredictedDeparture>.at(number: String, time: String): PredictedDeparture =
    filter { it.routeShortName == number && it.scheduled.toLocalTime().toString() == time }
        .minBy { it.scheduled.toInstant() }

val PredictedDeparture.waitingVehicle: String?
    get() = (state as? DepartureState.Waiting)?.vehicleId?.value
