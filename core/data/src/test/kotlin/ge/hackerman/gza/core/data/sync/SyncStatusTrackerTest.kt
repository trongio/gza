package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.MutableClock
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SyncStatusTrackerTest {
    private val clock = MutableClock()
    private val tracker = SyncStatusTracker(clock)
    private val key = SyncKey.Route(RouteId("1:R97493"))

    @Test
    fun `begin and end record the attempt and its error`() = runTest {
        assertEquals(KeyStatus.NONE, tracker.status(key).first())
        tracker.begin(key)
        assertEquals(KeyStatus(true, null, clock.now), tracker.current(key))
        tracker.end(key, SyncOutcome.Failed(SyncError.OFFLINE))
        assertEquals(KeyStatus(false, SyncError.OFFLINE, clock.now, 1), tracker.status(key).first())
        tracker.begin(key)
        // A running retry keeps showing the last error until it ends.
        assertEquals(SyncError.OFFLINE, tracker.current(key).lastError)
        tracker.end(key, SyncOutcome.Synced)
        assertEquals(null, tracker.current(key).lastError)
        assertEquals(KeyStatus.NONE, tracker.current(SyncKey.Stops))
    }

    @Test
    fun `no data while syncing is loading`() {
        assertEquals(
            CachedResult.Loading,
            cachedResult<String>(null, null, Freshness.STALE, KeyStatus(true, SyncError.OFFLINE, null))
        )
    }

    @Test
    fun `no data and no attempt yet is loading`() {
        assertEquals(CachedResult.Loading, cachedResult<String>(null, null, Freshness.STALE, KeyStatus.NONE))
    }

    @Test
    fun `no data after a failed attempt is unavailable`() {
        assertEquals(
            CachedResult.Unavailable(SyncError.OFFLINE),
            cachedResult<String>(null, null, Freshness.STALE, KeyStatus(false, SyncError.OFFLINE, clock.now))
        )
    }

    @Test
    fun `data is always shown, with the last error`() {
        val status = KeyStatus(inFlight = false, lastError = SyncError.NO_KEY, lastAttemptAt = clock.now)
        assertEquals(
            CachedResult.Data("x", clock.now, Freshness.STALE, SyncError.NO_KEY),
            cachedResult("x", clock.now, Freshness.STALE, status)
        )
        assertEquals(
            CachedResult.Data("x", clock.now, Freshness.FRESH, null),
            cachedResult("x", clock.now, Freshness.FRESH, KeyStatus(true, null, null))
        )
    }

    @Test
    fun `keys render as their sync_state value`() {
        assertEquals("stops", SyncKey.Stops.value)
        assertEquals("routes", SyncKey.Routes.toString())
        assertEquals("route:1:R97493", key.value)
        assertEquals("stop-routes:1:970", SyncKey.StopRoutes(StopId("1:970")).value)
    }

    @Test
    fun `failures in a row are counted and a success resets them`() {
        repeat(3) {
            tracker.begin(key)
            tracker.end(key, SyncOutcome.Failed(SyncError.OFFLINE))
        }
        assertEquals(3, tracker.current(key).consecutiveFailures)
        tracker.begin(key)
        tracker.abandon(key)
        assertEquals(3, tracker.current(key).consecutiveFailures)
        tracker.begin(key)
        tracker.end(key, SyncOutcome.UpToDate)
        assertEquals(0, tracker.current(key).consecutiveFailures)
    }
}
