package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import java.time.LocalDate
import java.time.ZonedDateTime

/** One timetable departure of one pattern from one stop. */
data class ScheduledDeparture(
    val routeId: RouteId,
    val pattern: PatternSuffix,
    val stopId: StopId,
    /** The service day the time is listed under: `24:05` keeps the earlier date. */
    val serviceDate: LocalDate,
    val scheduled: ZonedDateTime,
    /** The stop is the first of its pattern here, where a bus can wait between trips. */
    val atFirstStop: Boolean
)
