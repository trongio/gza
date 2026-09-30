package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.model.Freshness.FRESH
import ge.hackerman.gza.core.data.model.Freshness.STALE
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
}
