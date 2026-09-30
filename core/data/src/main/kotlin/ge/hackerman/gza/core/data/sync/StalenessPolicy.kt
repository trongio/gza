package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.model.Freshness
import java.time.Duration
import java.time.Instant

/**
 * How old cached data may get. Pure duration arithmetic on instants, so the device's time zone
 * never matters. Freshness is computed when a flow emits; it does not tick on its own.
 */
data class StalenessPolicy(
    val catalogMaxAge: Duration = DEFAULT_CATALOG_MAX_AGE,
    val routeMaxAge: Duration = DEFAULT_ROUTE_MAX_AGE,
    val stopRoutesMaxAge: Duration = DEFAULT_CATALOG_MAX_AGE,
    /** Routes used within this window are refreshed on app open; others wait for their next use. */
    val activeRouteWindow: Duration = DEFAULT_ACTIVE_ROUTE_WINDOW,
    /** A synced_at further in the future than this means the device clock moved: treat as stale. */
    val futureTolerance: Duration = DEFAULT_FUTURE_TOLERANCE,
    /** How long a key waits after its first failed attempt before a non-forced sync retries it. */
    val errorBackoffBase: Duration = DEFAULT_ERROR_BACKOFF_BASE,
    /** The longest wait, however many attempts failed in a row. */
    val errorBackoffMax: Duration = DEFAULT_ERROR_BACKOFF_MAX
) {
    fun freshness(syncedAt: Instant?, maxAge: Duration, now: Instant): Freshness = when {
        syncedAt == null -> Freshness.STALE
        syncedAt > now + futureTolerance -> Freshness.STALE
        Duration.between(syncedAt, now) >= maxAge -> Freshness.STALE
        else -> Freshness.FRESH
    }

    fun isStale(syncedAt: Instant?, maxAge: Duration, now: Instant): Boolean =
        freshness(syncedAt, maxAge, now) == Freshness.STALE

    /** Zero before any failure, then [errorBackoffBase] doubling per failure up to [errorBackoffMax]. */
    fun errorBackoff(consecutiveFailures: Int): Duration = if (consecutiveFailures <= 0) {
        Duration.ZERO
    } else {
        // Capped shift: a long run of failures must not overflow before the max applies.
        val doublings = minOf(consecutiveFailures - 1, MAX_BACKOFF_DOUBLINGS)
        minOf(errorBackoffBase.multipliedBy(1L shl doublings), errorBackoffMax)
    }

    /**
     * True while the last attempt of a key failed less than [errorBackoff] ago, so app open and
     * screens do not hammer a gateway that is down. A last attempt in the future means the
     * clock moved back: retry rather than wait out a window measured from the wrong time.
     */
    internal fun isBackingOff(status: KeyStatus, now: Instant): Boolean {
        val lastAttemptAt = status.lastAttemptAt
        return status.lastError != null &&
            lastAttemptAt != null &&
            lastAttemptAt <= now &&
            now < lastAttemptAt + errorBackoff(status.consecutiveFailures)
    }
}

// Stops and routes change by a handful a week; timetables get next week's dates daily.
private val DEFAULT_CATALOG_MAX_AGE: Duration = Duration.ofDays(7)
private val DEFAULT_ROUTE_MAX_AGE: Duration = Duration.ofHours(12)
private val DEFAULT_ACTIVE_ROUTE_WINDOW: Duration = Duration.ofDays(14)
private val DEFAULT_FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

// Offline usually lasts minutes; a gateway outage can last hours, where two hours between
// attempts is still quick enough once it is back.
private val DEFAULT_ERROR_BACKOFF_BASE: Duration = Duration.ofMinutes(5)
private val DEFAULT_ERROR_BACKOFF_MAX: Duration = Duration.ofHours(2)
private const val MAX_BACKOFF_DOUBLINGS = 30
