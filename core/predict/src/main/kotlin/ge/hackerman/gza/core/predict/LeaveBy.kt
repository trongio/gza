package ge.hackerman.gza.core.predict

import java.time.ZonedDateTime

/** PLAN.md 2.3: a small safety margin on top of the walk, 1 min unless the user picks up to 3. */
const val DEFAULT_BUFFER_MINUTES = 1
const val MAX_BUFFER_MINUTES = 3
const val MAX_WALK_MINUTES = 120

/**
 * When to leave home to catch this departure: its predicted time (a waiting bus's leave time,
 * a late bus's moving time) minus the walk and the buffer. Out-of-range values are clamped,
 * not rejected: they come from stored preferences, and a bad one must not break the screen.
 */
fun PredictedDeparture.leaveBy(walkMinutes: Int, bufferMinutes: Int = DEFAULT_BUFFER_MINUTES): ZonedDateTime =
    predicted.minusMinutes(
        walkMinutes.coerceIn(0, MAX_WALK_MINUTES).toLong() + bufferMinutes.coerceIn(0, MAX_BUFFER_MINUTES)
    )

data class LeaveOption(val departure: PredictedDeparture, val leaveBy: ZonedDateTime)

/** The departure to aim for and the one after it; both null when nothing can be caught. */
data class LeavePlan(val best: LeaveOption?, val fallback: LeaveOption?)

/**
 * Picks from options that can still be caught (leave-by not before [now]), earliest leave-by
 * first. Walks differ per stop, so options from several stops order by leave-by, not by
 * departure time.
 */
fun List<LeaveOption>.planFrom(now: ZonedDateTime): LeavePlan {
    val catchable = filter { !it.leaveBy.isBefore(now) }
        .sortedWith(compareBy<LeaveOption>({ it.leaveBy.toInstant() }, { it.departure.predicted.toInstant() }))
    return LeavePlan(best = catchable.getOrNull(0), fallback = catchable.getOrNull(1))
}
