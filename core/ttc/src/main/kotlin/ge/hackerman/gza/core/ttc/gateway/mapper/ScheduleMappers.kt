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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/** Parsed faithfully; choosing the period for a day is `:core:predict`'s job. */
internal fun List<ServicePeriodDto?>?.toRouteSchedule(routeId: RouteId, pattern: PatternSuffix): RouteSchedule =
    RouteSchedule(routeId, pattern, orEmpty().mapNotNull { it?.toServicePeriodOrNull() })

internal fun ServicePeriodDto.toServicePeriodOrNull(): ServicePeriod? {
    val from = dayOfWeekOrNull(fromDay)
    val to = dayOfWeekOrNull(toDay)
    return if (from == null || to == null) {
        null
    } else {
        ServicePeriod(
            fromDay = from,
            toDay = to,
            serviceDates = serviceDates.orEmpty().mapNotNullTo(LinkedHashSet()) { localDateOrNull(it) },
            stops = stops.orEmpty().mapIndexedNotNull { index, stop -> stop?.toScheduledStopOrNull(index) }
        )
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

private fun localDateOrNull(raw: String?): LocalDate? = raw?.let {
    try {
        LocalDate.parse(it.trim())
    } catch (_: DateTimeParseException) {
        null
    }
}
