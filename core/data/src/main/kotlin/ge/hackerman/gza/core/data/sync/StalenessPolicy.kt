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
    val futureTolerance: Duration = DEFAULT_FUTURE_TOLERANCE
) {
    fun freshness(syncedAt: Instant?, maxAge: Duration, now: Instant): Freshness = when {
        syncedAt == null -> Freshness.STALE
        syncedAt > now + futureTolerance -> Freshness.STALE
        Duration.between(syncedAt, now) >= maxAge -> Freshness.STALE
        else -> Freshness.FRESH
    }

    fun isStale(syncedAt: Instant?, maxAge: Duration, now: Instant): Boolean =
        freshness(syncedAt, maxAge, now) == Freshness.STALE
}

// Stops and routes change by a handful a week; timetables get next week's dates daily.
private val DEFAULT_CATALOG_MAX_AGE: Duration = Duration.ofDays(7)
private val DEFAULT_ROUTE_MAX_AGE: Duration = Duration.ofHours(12)
private val DEFAULT_ACTIVE_ROUTE_WINDOW: Duration = Duration.ofDays(14)
private val DEFAULT_FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)
