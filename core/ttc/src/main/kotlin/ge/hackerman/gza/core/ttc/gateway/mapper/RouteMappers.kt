package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.Pattern
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.StopRef
import ge.hackerman.gza.core.ttc.gateway.dto.PatternDto
import ge.hackerman.gza.core.ttc.gateway.dto.PatternStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDetailDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopRefDto
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrNull

internal fun RouteDto.toRouteOrNull(): Route? = RouteId.ofOrNull(id)?.let { routeId ->
    Route(
        id = routeId,
        shortName = shortName.orFallback(routeId),
        longName = longName?.takeIf { it.isNotBlank() },
        color = RouteColor.ofHexOrNull(color),
        kind = KindMapping.fromMode(mode, routeId)
    )
}

internal fun List<RouteDto?>?.toRoutes(): List<Route> = mapEachOrMalformed { it.toRouteOrNull() }

/**
 * Null without a valid id, or when patterns were sent and none is usable; the client reports
 * either as a malformed response.
 */
internal fun RouteDetailDto.toRouteDetailOrNull(): RouteDetail? {
    val routeId = RouteId.ofOrNull(id)
    val mappedPatterns = patterns.mapEachOrNull { _, pattern -> pattern.toPatternOrNull() }
    return if (routeId == null || mappedPatterns == null) {
        null
    } else {
        RouteDetail(
            id = routeId,
            shortName = shortName.orFallback(routeId),
            color = RouteColor.ofHexOrNull(color),
            kind = KindMapping.fromMode(mode, routeId),
            patterns = mappedPatterns,
            defaultPattern = PatternSuffix.ofOrNull(defaultPatternSuffix)
        )
    }
}

internal fun PatternDto.toPatternOrNull(): Pattern? = PatternSuffix.ofOrNull(patternSuffix)?.let { suffix ->
    Pattern(
        suffix = suffix,
        directionId = directionId ?: suffix.directionId,
        firstStop = firstStop?.toStopRefOrNull(),
        lastStop = lastStop?.toStopRefOrNull(),
        headsign = headsign?.takeIf { it.isNotBlank() }
    )
}

internal fun StopRefDto.toStopRefOrNull(): StopRef? =
    StopId.ofOrNull(id)?.let { StopRef(it, name?.takeIf { name -> name.isNotBlank() }) }

/**
 * Keeps the gateway's order, which is travel order only for a single-pattern request, and
 * keeps duplicates (a loop visits a stop twice). Entries that say they do not serve
 * [pattern] are dropped as legitimately absent; an entry without the list is kept. Among the
 * rest, the all-misfit rule applies: entries sent but no usable stop is malformed.
 */
internal fun List<PatternStopDto?>?.toPatternStops(routeId: RouteId, pattern: PatternSuffix): PatternStops =
    PatternStops(
        routeId = routeId,
        pattern = pattern,
        stops = orEmpty()
            .filter { entry ->
                entry != null &&
                    (entry.patternSuffixes == null || pattern.value in entry.patternSuffixes)
            }
            .mapEachOrMalformed { it.stop?.toStopOrNull() }
    )

private fun String?.orFallback(routeId: RouteId): String = this?.takeIf { it.isNotBlank() } ?: routeId.localId
