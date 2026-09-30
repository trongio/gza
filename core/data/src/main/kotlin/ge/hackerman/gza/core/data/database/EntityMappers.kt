package ge.hackerman.gza.core.data.database

import ge.hackerman.gza.core.data.database.dao.RouteDataRows
import ge.hackerman.gza.core.data.database.entity.PatternEntity
import ge.hackerman.gza.core.data.database.entity.PatternStopEntity
import ge.hackerman.gza.core.data.database.entity.PolylineEntity
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.SchedulePeriodEntity
import ge.hackerman.gza.core.data.database.entity.ScheduleStopTimesEntity
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.model.Pattern
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop

// Domain objects from the gateway to rows. Ids are unique per table, so a list that repeats
// one keeps its first entry instead of aborting the whole transaction on the primary key.

/** The English list is the id list of record; Georgian only adds names (TTC_API.md: same ids). */
internal fun mergeStops(en: List<Stop>, ka: List<Stop>): List<StopEntity> {
    val kaNames = ka.associate { it.id.value to it.name }
    return en.distinctBy { it.id }.map { it.toEntity(nameEn = it.name, nameKa = kaNames[it.id.value]) }
}

internal fun mergeRoutes(en: List<Route>, ka: List<Route>): List<RouteEntity> {
    val kaNames = ka.associate { it.id.value to it.longName }
    return en.distinctBy { it.id }.map { it.toEntity(longNameEn = it.longName, longNameKa = kaNames[it.id.value]) }
}

internal fun Stop.toEntity(nameEn: String?, nameKa: String?): StopEntity = StopEntity(
    id = id.value,
    code = code,
    nameEn = nameEn?.takeIf { it.isNotBlank() },
    nameKa = nameKa?.takeIf { it.isNotBlank() },
    lat = location.lat,
    lon = location.lon,
    kind = kind
)

internal fun Route.toEntity(longNameEn: String?, longNameKa: String?): RouteEntity = RouteEntity(
    id = id.value,
    shortName = shortName,
    longNameEn = longNameEn?.takeIf { it.isNotBlank() },
    longNameKa = longNameKa?.takeIf { it.isNotBlank() },
    color = color?.rgb,
    kind = kind
)

/**
 * Rows for one route: the English detail decides the patterns, the Georgian one only adds
 * names. Language-neutral data (stop order, times, shapes) comes from English responses.
 */
internal fun routeDataRows(
    detailEn: RouteDetail,
    detailKa: RouteDetail?,
    patternStops: List<PatternStops>,
    schedules: List<RouteSchedule>,
    polylines: List<RoutePolyline>
): RouteDataRows {
    val routeId = detailEn.id.value
    val kaPatterns = detailKa?.patterns.orEmpty().associateBy { it.suffix }
    val stopRows = patternStops.distinctBy { it.pattern }.flatMap { pattern ->
        pattern.stops.mapIndexed { seq, stop -> PatternStopEntity(routeId, pattern.pattern.value, seq, stop.id.value) }
    }
    return RouteDataRows(
        patterns = detailEn.patterns.distinctBy { it.suffix }.map { it.toEntity(routeId, kaPatterns[it.suffix]) },
        patternStops = stopRows,
        polylines = polylines.distinctBy { it.pattern }.map {
            PolylineEntity(routeId, it.pattern.value, it.encoded.value, it.color?.rgb)
        },
        periods = schedules.distinctBy { it.pattern }.flatMap { it.periodEntities(routeId) },
        stopTimes = schedules.distinctBy { it.pattern }.flatMap { it.stopTimeEntities(routeId) },
        stopsFromPatterns = patternStops.flatMap { it.stops }.distinctBy { it.id }.map {
            it.toEntity(nameEn = it.name, nameKa = null)
        },
        routeFromDetail = RouteEntity(
            id = routeId,
            shortName = detailEn.shortName,
            longNameEn = null,
            longNameKa = null,
            color = detailEn.color?.rgb,
            kind = detailEn.kind
        )
    )
}

private fun Pattern.toEntity(routeId: String, ka: Pattern?): PatternEntity = PatternEntity(
    routeId = routeId,
    suffix = suffix.value,
    directionId = directionId,
    firstStopId = firstStop?.id?.value,
    firstStopNameEn = firstStop?.name,
    firstStopNameKa = ka?.firstStop?.takeIf { it.id == firstStop?.id }?.name,
    lastStopId = lastStop?.id?.value,
    lastStopNameEn = lastStop?.name,
    lastStopNameKa = ka?.lastStop?.takeIf { it.id == lastStop?.id }?.name,
    headsignEn = headsign,
    headsignKa = ka?.headsign
)

private fun RouteSchedule.periodEntities(routeId: String): List<SchedulePeriodEntity> =
    periods.mapIndexed { index, period ->
        SchedulePeriodEntity(
            routeId = routeId,
            suffix = pattern.value,
            periodIndex = index,
            fromDay = period.fromDay,
            toDay = period.toDay,
            serviceDates = period.serviceDates.sorted()
        )
    }

private fun RouteSchedule.stopTimeEntities(routeId: String): List<ScheduleStopTimesEntity> =
    periods.flatMapIndexed { index, period ->
        period.stops.mapIndexed { seq, stop ->
            ScheduleStopTimesEntity(
                routeId = routeId,
                suffix = pattern.value,
                periodIndex = index,
                seq = seq,
                position = stop.position,
                stopId = stop.stopId.value,
                times = PackedMinutes.pack(stop.times)
            )
        }
    }
