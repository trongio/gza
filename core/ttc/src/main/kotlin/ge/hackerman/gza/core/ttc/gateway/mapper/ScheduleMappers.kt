package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.dto.ScheduledStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.ServicePeriodDto
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** Parsed faithfully; choosing the period for a day is `:core:predict`'s job. */
internal fun List<ServicePeriodDto?>?.toRouteSchedule(routeId: RouteId, pattern: PatternSuffix): RouteSchedule =
    RouteSchedule(routeId, pattern, mapEachOrMalformed { it.toServicePeriodOrNull() })

/** Null without both days, or when dates or stops were sent and none of them is usable. */
internal fun ServicePeriodDto.toServicePeriodOrNull(): ServicePeriod? {
    val from = dayOfWeekOrNull(fromDay)
    val to = dayOfWeekOrNull(toDay)
    val dates = serviceDates.mapEachOrNull { _, date -> localDateOrNull(date) }
    val mappedStops = stops.mapEachOrNull { index, stop -> stop.toScheduledStopOrNull(index) }
    return if (from == null || to == null || dates == null) {
        null
    } else {
        mappedStops?.let { ServicePeriod(fromDay = from, toDay = to, serviceDates = LinkedHashSet(dates), stops = it) }
    }
}

/** A missing position falls back to the 1-based index; unparseable times are dropped in order. */
internal fun ScheduledStopDto.toScheduledStopOrNull(index: Int): ScheduledStop? = StopId.ofOrNull(id)?.let {
    ScheduledStop(
        stopId = it,
        name = name?.takeIf { name -> name.isNotBlank() },
        position = position ?: (index + 1),
        times = arrivalTimes.orEmpty().split(',').mapNotNull(ServiceMinute::parseOrNull)
    )
}

private fun dayOfWeekOrNull(raw: String?): DayOfWeek? {
    val name = raw?.trim()?.uppercase(Locale.ROOT)
    return DayOfWeek.entries.firstOrNull { it.name == name }
}

private fun localDateOrNull(raw: String): LocalDate? = try {
    LocalDate.parse(raw.trim())
} catch (_: DateTimeParseException) {
    null
}
