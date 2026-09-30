package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.model.Freshness.FRESH
import ge.hackerman.gza.core.data.model.Freshness.STALE
import ge.hackerman.gza.core.data.model.SyncError
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class StalenessPolicyTest {
    private val policy = StalenessPolicy()
    private val now = Instant.parse("2026-09-30T14:00:00Z")

    @Test
    fun `never synced is stale`() {
        assertEquals(STALE, policy.freshness(null, policy.routeMaxAge, now))
    }

    @Test
    fun `a route is fresh for just under 12 hours`() {
        assertEquals(FRESH, policy.freshness(now - Duration.ofHours(11).plusMinutes(59), policy.routeMaxAge, now))
        assertEquals(STALE, policy.freshness(now - Duration.ofHours(12), policy.routeMaxAge, now))
    }

    @Test
    fun `the catalog is fresh for a week`() {
        assertEquals(FRESH, policy.freshness(now - Duration.ofDays(6), policy.catalogMaxAge, now))
        assertEquals(STALE, policy.freshness(now - Duration.ofDays(7), policy.catalogMaxAge, now))
        assertEquals(Duration.ofDays(7), policy.stopRoutesMaxAge)
        assertEquals(Duration.ofDays(14), policy.activeRouteWindow)
    }

    @Test
    fun `a sync from the future means the clock moved`() {
        assertEquals(STALE, policy.freshness(now + Duration.ofHours(1), policy.routeMaxAge, now))
        assertEquals(FRESH, policy.freshness(now + Duration.ofMinutes(4), policy.routeMaxAge, now))
    }

    @Test
    fun `the answer does not depend on any zone`() {
        val synced = now - Duration.ofHours(11)
        listOf("Asia/Tbilisi", "America/New_York", "Pacific/Kiritimati").forEach { zone ->
            val local = now.atZone(ZoneId.of(zone)).toInstant()
            assertEquals(FRESH, policy.freshness(synced, policy.routeMaxAge, local))
        }
        assertTrue(policy.isStale(null, policy.routeMaxAge, now))
        assertFalse(policy.isStale(synced, policy.routeMaxAge, now))
    }

    @Test
    fun `the error backoff starts at 5 minutes and doubles to a 2 hour cap`() {
        val minutes = listOf(0, 1, 2, 3, 4, 5, 6, 7, 100, Int.MAX_VALUE).map { policy.errorBackoff(it).toMinutes() }
        assertEquals(listOf(0L, 5L, 10L, 20L, 40L, 80L, 120L, 120L, 120L, 120L), minutes)
        assertEquals(Duration.ZERO, policy.errorBackoff(-1))
    }

    @Test
    fun `a failed key backs off until the last millisecond of its window`() {
        val oneMilli = Duration.ofMillis(1)
        val first = KeyStatus(false, SyncError.OFFLINE, now, consecutiveFailures = 1)
        assertTrue(policy.isBackingOff(first, now))
        assertTrue(policy.isBackingOff(first, now + Duration.ofMinutes(5) - oneMilli))
        assertFalse(policy.isBackingOff(first, now + Duration.ofMinutes(5)))
        val second = first.copy(consecutiveFailures = 2)
        assertTrue(policy.isBackingOff(second, now + Duration.ofMinutes(10) - oneMilli))
        assertFalse(policy.isBackingOff(second, now + Duration.ofMinutes(10)))
        val many = first.copy(consecutiveFailures = 12)
        assertTrue(policy.isBackingOff(many, now + Duration.ofHours(2) - oneMilli))
        assertFalse(policy.isBackingOff(many, now + Duration.ofHours(2)))
    }

    @Test
    fun `no error, no attempt, or an attempt in the future never backs off`() {
        assertFalse(policy.isBackingOff(KeyStatus.NONE, now))
        assertFalse(policy.isBackingOff(KeyStatus(true, null, now), now))
        assertFalse(policy.isBackingOff(KeyStatus(false, SyncError.OFFLINE, null, 1), now))
        // The clock moved back: the window would be measured from the wrong time.
        val future = KeyStatus(false, SyncError.OFFLINE, now + Duration.ofMinutes(1), 1)
        assertFalse(policy.isBackingOff(future, now))
    }
}
