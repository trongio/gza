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
data class LayoverMemory(val vehicles: Map<VehicleId, ParkedVehicle>) {
    companion object {
        val Empty = LayoverMemory(emptyMap())
    }
}

data class ParkedVehicle(
    val routeId: RouteId,
    /** The pattern it was counted for, which may differ from its label (buses are relabelled while parked). */
    val pattern: PatternSuffix,
    /** The first poll that saw it parked: the best guess at its arrival. */
    val firstSeen: Instant,
    /** The scheduled time of the row it was given, or null when it got none. */
    val waitingFor: ZonedDateTime?
)
