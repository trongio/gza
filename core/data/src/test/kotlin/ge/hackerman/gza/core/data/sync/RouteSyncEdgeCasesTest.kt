package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.data.testing.failInsertOf
import ge.hackerman.gza.core.model.PatternSuffix
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteSyncEdgeCasesTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val db = graph.db
    private val r326 = FixtureDomain.routeId("326")
    private val outbound = PatternSuffix("0:01")

    @After
    fun tearDown() = db.close()

    private suspend fun stopTimeRows(suffix: PatternSuffix) =
        db.routeDataDao().getScheduleStopTimes(r326.value, suffix.value).size

    @Test
    fun `a storage failure writing a route keeps the old data and says storage`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        val before = db.routeDataDao().getPatternStops(r326.value, outbound.value)
        graph.clock.advanceBy(Duration.ofHours(13))
        // The last stop of the pattern fails: every delete and the other inserts already ran.
        db.failInsertOf("pattern_stops", before.last().stopId, column = "stop_id")
        val outcome = graph.routes.refreshRouteIfStale(r326)
        assertEquals(SyncOutcome.Failed(SyncError.STORAGE), outcome)
        assertEquals(before, db.routeDataDao().getPatternStops(r326.value, outbound.value))
        assertEquals(2, db.routeDataDao().countPatterns(r326.value))
    }

    @Ignore(
        "BUG: a schedule whose periods all come back with no stop rows replaces the cached timetable " +
            "with an empty one (RouteSync.keepCachedWhereEmpty only checks for a missing period list)"
    )
    @Test
    fun `a timetable whose periods came back with no rows keeps the cached rows`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        val before = stopTimeRows(outbound)
        assertTrue(before > 0)
        graph.clock.advanceBy(Duration.ofHours(13))
        gateway.scheduleOverride = { id, pattern ->
            val real = FixtureDomain.schedule(id, pattern)
            if (pattern == outbound) real.copy(periods = real.periods.map { it.copy(stops = emptyList()) }) else real
        }
        graph.routes.refreshRouteIfStale(r326)
        assertEquals(before, stopTimeRows(outbound))
    }

    @Ignore(
        "BUG: route data whose catalog row a later catalog sync removed reads as Loading forever " +
            "(toBundle needs the route row although RouteBundle.route is nullable), and " +
            "refreshRouteIfStale says UpToDate, so nothing puts the row back for 12 h"
    )
    @Test
    fun `cached route data stays visible when the catalog no longer lists the route`() = runBlocking {
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        gateway.routesOverride = { language -> FixtureDomain.routes(language).filterNot { it.id == r326 } }
        assertEquals(SyncOutcome.Synced, graph.catalogSync.syncIfStale())
        assertEquals(2, db.routeDataDao().countPatterns(r326.value))
        val result = graph.routes.observeRoute(r326).first()
        assertTrue("got $result", result is CachedResult.Data)
    }
}
