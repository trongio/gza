package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.BoundingBox
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.TBILISI_ZONE
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.TripOptimize
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.TripTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How query values are written. Always [Locale.ROOT]: a ru or ka device would write a decimal comma. */
internal object QueryFormat {
    /** Stops, stop routes and the full route list: everything Gza shows. */
    const val ROUTE_MODES = "BUS,SUBWAY,GONDOLA"

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    fun place(at: LatLon): String = "${coordinate(at.lat)},${coordinate(at.lon)}"

    /** The gateway's order: min lon, min lat, max lon, max lat. */
    fun bbox(box: BoundingBox): String =
        listOf(box.minLon, box.minLat, box.maxLon, box.maxLat).joinToString(",", transform = ::coordinate)

    fun patterns(patterns: List<PatternSuffix>): String = patterns.joinToString(",") { it.value }

    /** Walking is always allowed; a minibus is a `BUS` to the planner. */
    fun planModes(kinds: Set<TransportKind>): String = buildList {
        add("WALK")
        if (TransportKind.METRO in kinds) add("SUBWAY")
        if (TransportKind.BUS in kinds || TransportKind.MINIBUS in kinds) add("BUS")
        if (TransportKind.CABLE_CAR in kinds) add("GONDOLA")
    }.joinToString(",")

    fun departMode(time: TripTime): String = when (time) {
        TripTime.LeaveNow -> "leaveNow"
        is TripTime.DepartAt -> "departAt"
        is TripTime.ArriveBy -> "arriveBy"
    }

    /** Tbilisi wall-clock `yyyy-MM-dd` and `HH:mm`, or null for [TripTime.LeaveNow]. */
    fun dateAndTime(time: TripTime): Pair<String, String>? {
        val at = when (time) {
            TripTime.LeaveNow -> null
            is TripTime.DepartAt -> time.at
            is TripTime.ArriveBy -> time.at
        }
        return at?.atZone(TBILISI_ZONE)?.let { it.format(DATE) to it.format(TIME) }
    }

    fun optimize(optimize: TripOptimize): String = when (optimize) {
        TripOptimize.QUICK -> "quick"
        TripOptimize.LESS_WALKING -> "lessWalking"
    }

    /** In the order the web app sends them; `date` and `time` only when not leaving now. */
    fun planQuery(request: TripRequest, language: Language): Map<String, String> = buildMap {
        put("fromPlace", place(request.from))
        put("toPlace", place(request.to))
        put("departMode", departMode(request.time))
        dateAndTime(request.time)?.let { (date, time) ->
            put("date", date)
            put("time", time)
        }
        put("modes", planModes(request.kinds))
        put("optimize", optimize(request.optimize))
        put("locale", language.code)
    }
}
