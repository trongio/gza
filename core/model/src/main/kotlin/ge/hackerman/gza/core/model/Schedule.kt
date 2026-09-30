package ge.hackerman.gza.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** The timetable of one pattern. Which period applies on a given day is `:core:predict`'s call. */
data class RouteSchedule(val routeId: RouteId, val pattern: PatternSuffix, val periods: List<ServicePeriod>)

/**
 * Days [fromDay]..[toDay] (can be a single day) share [stops]. [serviceDates] only cover the
 * current week, so past it match by weekday.
 */
data class ServicePeriod(
    val fromDay: DayOfWeek,
    val toDay: DayOfWeek,
    val serviceDates: Set<LocalDate>,
    val stops: List<ScheduledStop>
)

/** At the first stop of a pattern, [times] are departures. */
data class ScheduledStop(val stopId: StopId, val name: String?, val position: Int, val times: List<ServiceMinute>)

/** Minutes after the service day's midnight. May be >= 1440: "24:05" is 00:05 the next calendar day. */
@JvmInline
value class ServiceMinute(val minutes: Int) {
    init {
        require(minutes in 0 until MAX_HOURS * MINUTES_PER_HOUR) { "service minute out of range: $minutes" }
    }

    /** The wall-clock time in Tbilisi on the service day [serviceDate]. */
    fun atDate(serviceDate: LocalDate): ZonedDateTime =
        serviceDate.atStartOfDay(TBILISI_ZONE).plusMinutes(minutes.toLong())

    companion object {
        /** `H:mm` or `HH:mm`, hours 0..47, minutes 0..59; anything else is null. */
        fun parseOrNull(raw: String): ServiceMinute? {
            val match = TIME.matchEntire(raw.trim()) ?: return null
            val hours = match.groupValues[1].toInt()
            val minutes = match.groupValues[2].toInt()
            return if (hours < MAX_HOURS && minutes < MINUTES_PER_HOUR) {
                ServiceMinute(hours * MINUTES_PER_HOUR + minutes)
            } else {
                null
            }
        }
    }
}

private val TIME = Regex("""(\d{1,2}):(\d{2})""")
private const val MINUTES_PER_HOUR = 60

// A service day can run past midnight, but never past a second one.
private const val MAX_HOURS = 48
