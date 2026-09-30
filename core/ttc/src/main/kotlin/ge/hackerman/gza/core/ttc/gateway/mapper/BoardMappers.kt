package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.BoardArrival
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.dto.BoardArrivalDto
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed
import java.time.Instant

/** Minutes are copied raw: 0 at a terminus, large negatives and nulls are all kept as hints. */
internal fun BoardArrivalDto.toBoardArrivalOrNull(): BoardArrival? = shortName?.takeIf { it.isNotBlank() }?.let {
    BoardArrival(
        routeShortName = it,
        pattern = PatternSuffix.ofOrNull(patternSuffix),
        headsign = headsign?.takeIf { headsign -> headsign.isNotBlank() },
        color = RouteColor.ofHexOrNull(color),
        kind = KindMapping.fromMode(vehicleMode, null),
        realtime = realtime ?: false,
        realtimeMinutesHint = realtimeArrivalMinutes,
        scheduledMinutesHint = scheduledArrivalMinutes
    )
}

internal fun List<BoardArrivalDto?>?.toStopBoard(stopId: StopId, fetchedAt: Instant): StopBoard =
    StopBoard(stopId, fetchedAt, mapEachOrMalformed { it.toBoardArrivalOrNull() })
