package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.VehicleId
import java.time.Instant
import java.time.ZonedDateTime

/**
 * What the engine saw parked at one stop on the previous poll. The engine stays a pure
 * function: the caller keeps the memory it returns and passes it back on the next call, which
 * gives a bus's arrival time and the departure it was waiting for.
 */
data class LayoverMemory(val vehicles: Map<ParkedKey, ParkedVehicle>) {
    companion object {
        val Empty = LayoverMemory(emptyMap())
    }
}

/**
 * A vehicle id is only trusted within one route's positions: the same id under another route
 * is another history, so entries are keyed by both.
 */
data class ParkedKey(val routeId: RouteId, val vehicleId: VehicleId)

data class ParkedVehicle(
    /** The pattern it was counted for, which may differ from its label (buses are relabelled while parked). */
    val pattern: PatternSuffix,
    /** The first poll that saw it parked: the best guess at its arrival. */
    val firstSeen: Instant,
    /** The scheduled time of the row it was given, or null when it got none. */
    val waitingFor: ZonedDateTime?,
    /** The last poll that saw it parked; an entry is kept through a short gap in the feed. */
    val lastSeen: Instant = firstSeen,
    /**
     * Seen in the feed, since [lastSeen], but not parked at this stop: the bus has left, so a
     * poll that leaves it out must not hold its row. Cleared when it is seen parked here again.
     */
    val seenMoving: Boolean = false
)
