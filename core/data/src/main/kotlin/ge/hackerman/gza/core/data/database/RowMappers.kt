package ge.hackerman.gza.core.data.database

import ge.hackerman.gza.core.data.database.dao.PatternStopRow
import ge.hackerman.gza.core.data.database.dao.ScheduleRow
import ge.hackerman.gza.core.data.database.entity.PatternEntity
import ge.hackerman.gza.core.data.database.entity.PolylineEntity
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.model.EncodedPolyline
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.Pattern
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.StopRef
import ge.hackerman.gza.core.model.TransportKind

// Rows back to domain objects, names in the asked language with the other as a fallback.
// A row that cannot make a valid object (no name at all, bad id or coordinates) is dropped.

internal fun pickName(language: Language, en: String?, ka: String?): String? =
    if (language == Language.KA) ka ?: en else en ?: ka

internal fun StopEntity.toStop(language: Language): Stop? =
    stopOrNull(id, code, pickName(language, nameEn, nameKa), lat, lon, kind)

internal fun RouteEntity.toRoute(language: Language): Route? = RouteId.ofOrNull(id)?.let {
    Route(it, shortName, pickName(language, longNameEn, longNameKa), routeColor(color), kind)
}

internal fun PatternStopRow.toStop(language: Language): Stop? =
    stopOrNull(stopId, code, pickName(language, nameEn, nameKa), lat, lon, kind ?: TransportKind.UNKNOWN)

@Suppress("LongParameterList") // The columns of a stop.
private fun stopOrNull(
    id: String,
    code: String?,
    name: String?,
    lat: Double?,
    lon: Double?,
    kind: TransportKind
): Stop? {
    val stopId = StopId.ofOrNull(id)
    val location = LatLon.ofOrNull(lat, lon)
    return if (stopId != null && name != null && location != null) Stop(stopId, code, name, location, kind) else null
}

internal fun PatternEntity.toPattern(language: Language): Pattern? = PatternSuffix.ofOrNull(suffix)?.let {
    Pattern(
        suffix = it,
        directionId = directionId,
        firstStop = StopId.ofOrNull(firstStopId)?.let { id ->
            StopRef(id, pickName(language, firstStopNameEn, firstStopNameKa))
        },
        lastStop = StopId.ofOrNull(lastStopId)?.let { id ->
            StopRef(id, pickName(language, lastStopNameEn, lastStopNameKa))
        },
        headsign = pickName(language, headsignEn, headsignKa)
    )
}

internal fun PolylineEntity.toPolyline(): RoutePolyline? = PatternSuffix.ofOrNull(suffix)?.let {
    RoutePolyline(it, EncodedPolyline(encoded), routeColor(color))
}

private fun routeColor(rgb: Int?): RouteColor? = rgb?.takeIf { it in 0..MAX_RGB }?.let(::RouteColor)

private const val MAX_RGB = 0xFFFFFF

/** Timetables from joined rows, one per (route, pattern), in the order the rows come. */
internal fun List<ScheduleRow>.toSchedules(): List<RouteSchedule> =
    groupBy { it.routeId to it.suffix }.mapNotNull { (key, rows) ->
        val routeId = RouteId.ofOrNull(key.first) ?: return@mapNotNull null
        val pattern = PatternSuffix.ofOrNull(key.second) ?: return@mapNotNull null
        val periods = rows.groupBy { it.periodIndex }.values.map { periodRows ->
            val period = periodRows.first()
            ServicePeriod(
                fromDay = period.fromDay,
                toDay = period.toDay,
                serviceDates = period.serviceDates.toSet(),
                stops = periodRows.mapNotNull { it.toScheduledStop() }
            )
        }
        RouteSchedule(routeId, pattern, periods)
    }

// Names are not stored with timetables: the stops table has them in both languages.
private fun ScheduleRow.toScheduledStop(): ScheduledStop? {
    val id = StopId.ofOrNull(stopId)
    val bytes = times
    return if (id != null && bytes != null) {
        ScheduledStop(id, name = null, position = position ?: 0, times = PackedMinutes.unpack(bytes))
    } else {
        null
    }
}
