package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed

// Mapping rule for every mapper here: a missing identity or position drops the item, display
// fields fall back or stay null, raw hint values are copied unchanged. The one exception to
// dropping is the all-misfit rule (mapEachOrMalformed): a response list whose items are all
// dropped is malformed, and a nested list whose items are all dropped drops its parent.
// Otherwise mappers never throw.

/** Null without a valid id and location. A missing name falls back to the code, then the local id. */
internal fun StopDto.toStopOrNull(): Stop? {
    val stopId = StopId.ofOrNull(id)
    val location = LatLon.ofOrNull(lat, lon)
    return if (stopId == null || location == null) {
        null
    } else {
        Stop(
            id = stopId,
            code = code?.takeIf { it.isNotBlank() },
            name = name?.takeIf { it.isNotBlank() } ?: code?.takeIf { it.isNotBlank() } ?: stopId.localId,
            location = location,
            kind = KindMapping.fromMode(vehicleMode, null)
        )
    }
}

/** @throws kotlinx.serialization.SerializationException when stops were sent and none is usable. */
internal fun List<StopDto?>?.toStops(): List<Stop> = mapEachOrMalformed { it.toStopOrNull() }
