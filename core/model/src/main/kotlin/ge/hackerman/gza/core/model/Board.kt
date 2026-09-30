package ge.hackerman.gza.core.model

import java.time.Instant

/** TTC's arrivals board for one stop. Minutes in the rows are relative to [fetchedAt]. */
data class StopBoard(val stopId: StopId, val fetchedAt: Instant, val arrivals: List<BoardArrival>)

/**
 * One row of TTC's arrivals board. A hint, never truth: at a terminus the realtime value
 * counts buses pulling in, and reads 0 while a bus waits for its departure (PLAN.md 2.0).
 * GPS positions plus the timetable decide what to show.
 */
data class BoardArrival(
    /** The board has no route id; join to routes by short name (and pattern). */
    val routeShortName: String,
    val pattern: PatternSuffix?,
    val headsign: String?,
    val color: RouteColor?,
    /** From `vehicleMode` only, so a minibus shows as [TransportKind.BUS] here. */
    val kind: TransportKind,
    val realtime: Boolean,
    /** Raw minutes as sent. Reads 0 for a bus parked at the terminus: not an arrival. */
    val realtimeMinutesHint: Int?,
    /** Raw minutes as sent. Can be a large negative (-76) for a trip that never ran. */
    val scheduledMinutesHint: Int?
)
