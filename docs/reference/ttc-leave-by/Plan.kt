package ge.hackerman.bus.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sqrt

// Pure logic: no android.* imports, so all of it runs under plain JUnit.

val TBILISI: ZoneId = ZoneId.of("Asia/Tbilisi")

data class Route(val id: String, val number: String, val headsign: String, val color: Long)

/** Stop #970, Ana Politkovskaia St. The first stop of all three routes, outbound pattern 0:01. */
object HomeStop {
    const val ID = "1:970"
    const val NAME = "Politkovskaya St"
    const val LAT = 41.722055
    const val LON = 44.703114
    const val PATTERN = "0:01"

    val routes = listOf(
        Route("1:R29981", "301", "Saint Barbare District", 0xFF00A07C),
        Route("1:R97493", "326", "Baratashvili St", 0xFF00805F),
        Route("1:minibusR24579", "551", "Tbilisi Mall", 0xFF2F55C8),
    )
}

/** One block of a route's timetable, e.g. MONDAY..FRIDAY, as departure minutes after midnight. */
data class ServicePeriod(
    val fromDay: DayOfWeek,
    val toDay: DayOfWeek,
    val dates: Set<LocalDate>,
    val minutes: List<Int>,
)

/** Prefer the period that lists the date explicitly (covers holidays), else match by weekday. */
fun periodFor(periods: List<ServicePeriod>, date: LocalDate): ServicePeriod? =
    periods.firstOrNull { date in it.dates }
        ?: periods.firstOrNull { date.dayOfWeek.value in it.fromDay.value..it.toDay.value }

/** "7:05" or "24:10" to minutes after midnight of the service day. */
fun parseClock(s: String): Int? {
    val parts = s.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

data class Departure(val route: Route, val at: ZonedDateTime)

/** Today's and tomorrow's departures for the enabled routes, sorted by time. */
fun departures(
    schedules: Map<String, List<ServicePeriod>>,
    enabled: Set<String>,
    now: ZonedDateTime,
): List<Departure> {
    val today = now.toLocalDate()
    val out = mutableListOf<Departure>()
    for (route in HomeStop.routes) {
        if (route.number !in enabled) continue
        val periods = schedules[route.id] ?: continue
        for (date in listOf(today, today.plusDays(1))) {
            val period = periodFor(periods, date) ?: continue
            val midnight = date.atStartOfDay(now.zone)
            period.minutes.forEach { out += Departure(route, midnight.plusMinutes(it.toLong())) }
        }
    }
    return out.sortedBy { it.at }
}

/** Metres between two points; an equirectangular approximation is plenty at city scale. */
fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val k = 111_320.0
    val dLat = (lat2 - lat1) * k
    val dLon = (lon2 - lon1) * k * cos(Math.toRadians((lat1 + lat2) / 2))
    return sqrt(dLat * dLat + dLon * dLon)
}

const val WAITING_RADIUS_M = 120.0

/** A bus with no bus at the stop this close to departure may leave late. */
const val NOT_ARRIVED_WINDOW_MIN = 12L

enum class LiveState {
    /** A bus of this route is parked at the stop: it will leave on the timetable. */
    WAITING,
    /** Departure is soon and no bus of this route is at the stop yet. */
    NOT_ARRIVED,
    /** Too far ahead to say, or no live data. */
    UNKNOWN,
}

data class PlannedDeparture(
    val departure: Departure,
    val leaveBy: ZonedDateTime,
    val live: LiveState,
)

/**
 * Only the next departure of each route gets a live state: the parked bus is the one that
 * leaves next, and says nothing about the trips after it. The arrivals board is ignored on
 * purpose; at a first stop it reports buses pulling in, not departures.
 *
 * [waiting] is buses parked at the stop per route number, or null when the live feed failed.
 */
fun plan(
    deps: List<Departure>,
    waiting: Map<String, Int>?,
    walkMinutes: Int,
    now: ZonedDateTime,
): List<PlannedDeparture> {
    val seenRoute = mutableSetOf<String>()
    return deps.filter { !it.at.isBefore(now.minusMinutes(1)) }.map { d ->
        val next = !d.at.isBefore(now) && seenRoute.add(d.route.number)
        val parked = waiting?.get(d.route.number)
        val state = when {
            !next || parked == null -> LiveState.UNKNOWN
            parked > 0 -> LiveState.WAITING
            d.at.isBefore(now.plusMinutes(NOT_ARRIVED_WINDOW_MIN)) -> LiveState.NOT_ARRIVED
            else -> LiveState.UNKNOWN
        }
        PlannedDeparture(d, d.at.minusMinutes(walkMinutes.toLong()), state)
    }
}
