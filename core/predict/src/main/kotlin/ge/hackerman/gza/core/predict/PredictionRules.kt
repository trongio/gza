package ge.hackerman.gza.core.predict

import java.time.Duration

/**
 * The tunable numbers of the engine. Each default is justified against the recorded fixtures
 * in `docs/tasks/plans/T05.md` (3.6); Phase 6 may learn some of them per route.
 */
data class PredictionRules(
    /**
     * How far from a pattern's first stop a bus with no heading and no next stop still counts
     * as waiting there. Real lay-by buses waited 318 m and 442 m from the stop; the heading
     * rule, not the distance, keeps moving buses out.
     */
    val layoverRadiusMeters: Double = 500.0,
    /** A bus that has just pulled in never leaves sooner than this after it was first seen. */
    val minTurnaround: Duration = Duration.ofMinutes(2),
    /** Inside this window before the next first-stop departure a bus is normally already there. */
    val noBusWindow: Duration = Duration.ofMinutes(NO_BUS_WINDOW_MINUTES),
    /** How long a still parked bus keeps its missed departure as late before it is dropped. */
    val lateLookback: Duration = Duration.ofMinutes(LATE_LOOKBACK_MINUTES),
    /** A late bus is predicted to leave this long after now, so its time moves on each poll. */
    val lateStep: Duration = Duration.ofMinutes(1),
    /** A row stays listed this long after its time, so it does not vanish the second it passes. */
    val pastGrace: Duration = Duration.ofMinutes(1),
    /**
     * A remembered bus missing from a poll keeps its entry this long after it was last seen:
     * the gateway sometimes leaves a parked bus out of one poll (4 polls at T07's 15 s).
     */
    val memoryDropout: Duration = Duration.ofMinutes(1),
    /**
     * An entry last seen longer ago than this is a new sighting when the bus shows up again,
     * so an old first sighting never waives a turnaround. Above the longest observed layover
     * (20 min), far below a day.
     */
    val memoryMaxAge: Duration = Duration.ofMinutes(MEMORY_MAX_AGE_MINUTES),
    /** Positions older than this count as no live data. */
    val maxPositionsAge: Duration = Duration.ofMinutes(2),
    /**
     * Positions stamped up to this far after now still count as fresh (the phone clock runs
     * behind). Further ahead the stamp is not trusted: with no bound, one wrong clock would
     * make a snapshot fresh for as long as it is kept.
     */
    val maxClockSkew: Duration = Duration.ofMinutes(2)
) {
    companion object {
        val Default = PredictionRules()
    }
}

// Prototype NOT_ARRIVED_WINDOW_MIN, proven on the phone; headways at 1:970 are 10 to 20 min.
private const val NO_BUS_WINDOW_MINUTES = 12L

private const val MEMORY_MAX_AGE_MINUTES = 30L

// At or below the shortest daytime headway at 1:970 (551, 10 min).
private const val LATE_LOOKBACK_MINUTES = 10L
