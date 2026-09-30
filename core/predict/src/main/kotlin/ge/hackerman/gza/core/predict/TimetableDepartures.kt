package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.StopId
import java.time.LocalDate

/**
 * Departures of this pattern from [stopId] on each of [serviceDates], sorted by time.
 *
 * Times at a pattern's last stop are arrivals at the end of the line, not departures (326
 * `1:01` lists `1:970` last with `24:02`), so they are left out. A stop a loop lists twice
 * gives one row per listing.
 */
fun RouteSchedule.departuresAt(stopId: StopId, serviceDates: List<LocalDate>): List<ScheduledDeparture> =
    serviceDates.flatMap { date ->
        val period = periods.periodFor(date) ?: return@flatMap emptyList()
        val first = period.stops.minOfOrNull { it.position }
        val last = period.stops.maxOfOrNull { it.position }
        period.stops
            .filter { it.stopId == stopId }
            .filterNot { period.stops.size > 1 && it.position == last }
            .flatMap { entry ->
                entry.times.map { time ->
                    ScheduledDeparture(
                        routeId = routeId,
                        pattern = pattern,
                        stopId = stopId,
                        serviceDate = date,
                        scheduled = time.atDate(date),
                        atFirstStop = entry.position == first
                    )
                }
            }
    }.sortedBy { it.scheduled }

/** Any period lists [stopId], even as its last stop: the timetable is there, if empty today. */
fun RouteSchedule.servesStop(stopId: StopId): Boolean =
    periods.any { period -> period.stops.any { it.stopId == stopId } }
