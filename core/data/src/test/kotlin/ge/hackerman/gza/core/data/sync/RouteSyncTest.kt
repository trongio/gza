package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.FakeTtcGatewayClient
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.MutableClock
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteSyncTest {
    private val db: GzaDatabase = TestDatabase.inMemory()
    private val gateway = FakeTtcGatewayClient()
    private val clock = MutableClock()
    private val tracker = SyncStatusTracker(clock)
    private val sync = RouteSync(gateway, db, tracker, StalenessPolicy(), clock)
    private val dao = db.routeDataDao()

    private val r326 = FixtureDomain.routeId("326")
    private val r301 = FixtureDomain.routeId("301")
    private val r551 = FixtureDomain.routeId("551")
    private val r472 = FixtureDomain.routeId("472")
    private val outbound = PatternSuffix("0:01")
    private val inbound = PatternSuffix("1:01")

    @After
    fun tearDown() = db.close()

    private suspend fun counts(id: RouteId): List<Int> = listOf(outbound.value, inbound.value).flatMap { suffix ->
        listOf(
            dao.getPatternStops(id.value, suffix).size,
            dao.getSchedulePeriods(id.value, suffix).size,
            dao.getScheduleStopTimes(id.value, suffix).size
        )
    } + dao.getPolylines(id.value).size + dao.countPatterns(id.value)

    @Test
    fun `first use of 326 makes exactly 7 requests, language neutral ones in english`() = runBlocking {
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(r326))
        val id = r326.value
        assertEquals(
            setOf(
                "route $id EN",
                "route $id KA",
                "pattern-stops $id 0:01 EN",
                "pattern-stops $id 1:01 EN",
                "schedule $id 0:01 EN",
                "schedule $id 1:01 EN",
                "polylines $id 1:01,0:01"
            ),
            gateway.calls.toSet()
        )
        assertEquals(7, gateway.calls.size)
        assertEquals(listOf(46, 2, 92, 45, 2, 90, 2, 2), counts(r326))
        val state = db.syncStateDao().get("route:$id")!!
        assertEquals(clock.now, state.syncedAt)
        assertEquals(clock.now, state.lastUsedAt)
    }

    @Test
    fun `472 uses the pattern suffixes its detail names`() = runBlocking {
        sync.syncIfStale(r472)
        assertEquals(listOf("0:03", "1:03"), dao.getPatterns(r472.value).map { it.suffix })
        assertTrue(gateway.calls.any { it == "schedule ${r472.value} 0:03 EN" })
        assertTrue(dao.getPatternStops(r472.value, "1:03").isNotEmpty())
    }

    @Test
    fun `within 12 hours it is up to date but the use is recorded, after 12 hours it refetches`() = runBlocking {
        val start = clock.now
        sync.syncIfStale(r326)
        gateway.calls.clear()
        clock.advanceBy(Duration.ofHours(11))
        assertEquals(SyncOutcome.UpToDate, sync.syncIfStale(r326))
        assertTrue(gateway.calls.isEmpty())
        val state = db.syncStateDao().get("route:${r326.value}")!!
        assertEquals(start, state.syncedAt)
        assertEquals(clock.now, state.lastUsedAt)
        clock.advanceBy(Duration.ofHours(1))
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(r326))
        assertEquals(7, gateway.calls.size)
    }

    @Test
    fun `a use is recorded at most once an hour`() = runBlocking {
        sync.syncIfStale(r326)
        val firstUse = clock.now
        clock.advanceBy(Duration.ofHours(1) - Duration.ofMillis(1))
        assertEquals(SyncOutcome.UpToDate, sync.syncIfStale(r326))
        assertEquals(firstUse, db.syncStateDao().get("route:${r326.value}")!!.lastUsedAt)
        clock.advanceBy(Duration.ofMillis(1))
        assertEquals(SyncOutcome.UpToDate, sync.syncIfStale(r326))
        assertEquals(clock.now, db.syncStateDao().get("route:${r326.value}")!!.lastUsedAt)
    }

    @Test
    fun `a use recorded in the future after the clock moved back is overwritten`() = runBlocking {
        sync.syncIfStale(r326)
        clock.advanceBy(Duration.ofMinutes(-3))
        assertEquals(SyncOutcome.UpToDate, sync.syncIfStale(r326))
        assertEquals(clock.now, db.syncStateDao().get("route:${r326.value}")!!.lastUsedAt)
    }

    @Test
    fun `a forced sync refetches even when fresh`() = runBlocking {
        sync.syncIfStale(r326)
        gateway.calls.clear()
        assertEquals(SyncOutcome.Synced, sync.sync(r326))
        assertEquals(7, gateway.calls.size)
    }

    @Test
    fun `one schedule failing keeps all the previous data`() = runBlocking {
        sync.syncIfStale(r326)
        val before = counts(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.patternStopsOverride =
            { id, s -> FixtureDomain.patternStops(id, s).let { it.copy(stops = it.stops.take(3)) } }
        gateway.scheduleOverride = { id, s ->
            if (s == inbound) throw TtcGatewayException.Http(500, null) else FixtureDomain.schedule(id, s)
        }
        assertEquals(SyncOutcome.Failed(SyncError.SERVER), sync.syncIfStale(r326))
        assertEquals(before, counts(r326))
    }

    @Test
    fun `an empty pattern list keeps the cached patterns`() = runBlocking {
        sync.syncIfStale(r326)
        val before = counts(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.routeOverride = { id, language -> FixtureDomain.route(id, language).copy(patterns = emptyList()) }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale(r326))
        assertEquals(before, counts(r326))
    }

    @Test
    fun `a pattern whose new timetable is empty keeps its cached one while the other updates`() = runBlocking {
        sync.syncIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.scheduleOverride = { id, s ->
            val real = FixtureDomain.schedule(id, s)
            if (s == inbound) RouteSchedule(id, s, emptyList()) else real.copy(periods = real.periods.take(1))
        }
        gateway.patternStopsOverride = { id, s ->
            if (s == inbound) PatternStops(id, s, emptyList()) else FixtureDomain.patternStops(id, s)
        }
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(r326))
        assertEquals(1, dao.getSchedulePeriods(r326.value, outbound.value).size)
        assertEquals(2, dao.getSchedulePeriods(r326.value, inbound.value).size)
        assertEquals(90, dao.getScheduleStopTimes(r326.value, inbound.value).size)
        assertEquals(45, dao.getPatternStops(r326.value, inbound.value).size)
        assertEquals(clock.now, db.syncStateDao().get("route:${r326.value}")?.syncedAt)
    }

    @Test
    fun `never more than 4 requests in flight across three routes`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val syncs = listOf(r301, r326, r551).map { async { sync.syncIfStale(it) } }
        while (gateway.calls.size < 4) yield()
        repeat(20) { yield() }
        assertEquals(4, gateway.calls.size)
        gate.complete(Unit)
        assertEquals(List(3) { SyncOutcome.Synced }, syncs.awaitAll())
        assertEquals(21, gateway.calls.size)
        assertTrue("max in flight ${gateway.maxInFlight}", gateway.maxInFlight <= 4)
    }

    @Test
    fun `refreshActiveRoutes refreshes stale routes used in the last 14 days only`() = runBlocking {
        sync.syncIfStale(r301)
        clock.advanceBy(Duration.ofDays(10))
        sync.syncIfStale(r326)
        sync.syncIfStale(r551)
        clock.advanceBy(Duration.ofDays(5))
        // 301 last used 15 days ago; 326 used 5 days ago and stale; 551 was just used and synced.
        sync.syncIfStale(r551)
        val usedAt551 = db.syncStateDao().get("route:${r551.value}")!!.lastUsedAt
        gateway.calls.clear()
        sync.refreshActiveRoutes()
        assertEquals(setOf(r326.value), gateway.calls.map { it.split(' ')[1] }.toSet())
        val state326 = db.syncStateDao().get("route:${r326.value}")!!
        assertEquals(clock.now, state326.syncedAt)
        // The refresh is not a use.
        assertEquals(clock.now - Duration.ofDays(5), state326.lastUsedAt)
        assertEquals(usedAt551, db.syncStateDao().get("route:${r551.value}")!!.lastUsedAt)
    }

    @Test
    fun `a rejected key is no key and keeps the data`() = runBlocking {
        sync.syncIfStale(r326)
        val before = counts(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.failure = TtcGatewayException.Http(401, null)
        assertEquals(SyncOutcome.Failed(SyncError.NO_KEY), sync.syncIfStale(r326))
        assertEquals(before, counts(r326))
    }

    @Test
    fun `a route that never synced records no use`() = runBlocking {
        gateway.failure = TtcGatewayException.Network(java.io.IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), sync.syncIfStale(r326))
        assertNull(db.syncStateDao().get("route:${r326.value}"))
    }

    @Test
    fun `app open backs off a failing route, 5 then 10 minutes, and a forced sync does not wait`() = runBlocking {
        sync.syncIfStale(r326)
        clock.advanceBy(Duration.ofHours(13))
        gateway.failure = TtcGatewayException.Http(500, null)
        gateway.calls.clear()
        sync.refreshActiveRoutes()
        assertTrue(gateway.calls.isNotEmpty())
        val key = SyncKey.Route(r326)
        assertEquals(1, tracker.current(key).consecutiveFailures)

        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(5) - Duration.ofMillis(1))
        sync.refreshActiveRoutes()
        assertEquals(SyncOutcome.Failed(SyncError.SERVER), sync.syncIfStale(r326))
        assertTrue(gateway.calls.isEmpty())

        clock.advanceBy(Duration.ofMillis(1))
        sync.refreshActiveRoutes()
        assertTrue(gateway.calls.isNotEmpty())
        assertEquals(2, tracker.current(key).consecutiveFailures)

        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(10) - Duration.ofMillis(1))
        sync.refreshActiveRoutes()
        assertTrue(gateway.calls.isEmpty())

        gateway.failure = null
        assertEquals(SyncOutcome.Synced, sync.sync(r326))
        assertEquals(0, tracker.current(key).consecutiveFailures)
    }
}
