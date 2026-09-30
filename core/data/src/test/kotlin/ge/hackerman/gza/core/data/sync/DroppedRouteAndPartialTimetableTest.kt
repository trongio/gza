package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two of the fix round's cache guards pushed further: a catalog that drops a route the user
 * has cached (then syncs of that route, a 404 for it, and its return), and timetables where
 * only some periods, or only some patterns, came back empty.
 */
@RunWith(AndroidJUnit4::class)
class DroppedRouteAndPartialTimetableTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val db = graph.db
    private val routeDao = db.routeDao()
    private val dataDao = db.routeDataDao()
    private val r326 = FixtureDomain.routeId("326")
    private val r301 = FixtureDomain.routeId("301")
    private val outbound = PatternSuffix("0:01")
    private val inbound = PatternSuffix("1:01")

    @After
    fun tearDown() = db.close()

    private fun dropFromCatalog(ids: List<RouteId>) {
        gateway.routesOverride = { language -> FixtureDomain.routes(language).filterNot { it.id in ids } }
    }

    private suspend fun resyncCatalog() {
        graph.clock.advanceBy(Duration.ofDays(8))
        assertEquals(SyncOutcome.Synced, graph.catalogSync.syncIfStale())
    }

    private suspend fun bundle(id: RouteId) = graph.routes.observeRoute(id).first()

    @Test
    fun `a dropped cached route survives the catalog, a route sync, a 404 and a second catalog`() = runBlocking {
        graph.catalogSync.syncIfStale()
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))

        // The catalog drops 326 (cached) and 301 (never opened).
        dropFromCatalog(listOf(r326, r301))
        resyncCatalog()
        assertNull("an uncached dropped route goes", routeDao.get(r301.value))
        assertNotNull("a cached dropped route stays", routeDao.get(r326.value))
        assertEquals(280 - 1, routeDao.count())
        assertTrue(bundle(r326) is CachedResult.Data)

        // The detail endpoint still serves it: a route sync neither duplicates nor loses the row.
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRoute(r326))
        assertEquals(280 - 1, routeDao.count())
        val synced = bundle(r326) as CachedResult.Data
        assertEquals(Freshness.FRESH, synced.freshness)
        assertEquals(null, synced.refreshError)

        // Then the route is gone for good: a 404 keeps the cached bundle, flagged.
        graph.clock.advanceBy(Duration.ofHours(13))
        gateway.routeOverride = { id, language ->
            if (id == r326) throw TtcGatewayException.Http(HTTP_NOT_FOUND, null) else FixtureDomain.route(id, language)
        }
        assertEquals(SyncOutcome.Failed(SyncError.SERVER), graph.routes.refreshRouteIfStale(r326))
        val gone = bundle(r326) as CachedResult.Data
        assertEquals(SyncError.SERVER, gone.refreshError)
        assertEquals(2, dataDao.countPatterns(r326.value))

        // A second catalog without it still keeps the row; the plausibility guard counts it.
        resyncCatalog()
        assertNotNull(routeDao.get(r326.value))
        assertTrue(bundle(r326) is CachedResult.Data)
    }

    @Test
    fun `a dropped route that comes back renamed takes the catalog's new row`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.routes.refreshRouteIfStale(r326)
        dropFromCatalog(listOf(r326))
        resyncCatalog()
        gateway.routesOverride = { language ->
            FixtureDomain.routes(language).map { if (it.id == r326) it.copy(longName = "Renamed $language") else it }
        }
        resyncCatalog()
        assertEquals("Renamed EN", routeDao.get(r326.value)?.longNameEn)
        assertEquals(280, routeDao.count())
    }

    @Test
    fun `a route opened only after the catalog dropped it gets its row from the detail and keeps it`() = runBlocking {
        graph.catalogSync.syncIfStale()
        dropFromCatalog(listOf(r301))
        resyncCatalog()
        assertNull(routeDao.get(r301.value))

        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r301))
        assertNotNull(routeDao.get(r301.value))
        assertTrue(bundle(r301) is CachedResult.Data)
        resyncCatalog()
        assertNotNull(routeDao.get(r301.value))
        assertTrue(bundle(r301) is CachedResult.Data)
    }

    // Documents the chosen behaviour (docs/tasks/plans/T04.md 3.5): the row stays while its
    // patterns are cached, so the route list keeps showing a route the gateway no longer lists.
    @Test
    fun `the route list keeps showing a dropped route while its data is cached`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.routes.refreshRouteIfStale(r326)
        dropFromCatalog(listOf(r326))
        resyncCatalog()
        val routes = (graph.routes.observeRoutes().first() as CachedResult.Data).value
        assertTrue(routes.any { it.id == r326 })
    }

    private fun scheduleWith(edit: (PatternSuffix, RouteSchedule) -> RouteSchedule) {
        gateway.scheduleOverride = { id, pattern -> edit(pattern, FixtureDomain.schedule(id, pattern)) }
    }

    private suspend fun rowsByPeriod(suffix: PatternSuffix): Map<Int, Int> =
        dataDao.getScheduleStopTimes(r326.value, suffix.value).groupingBy { it.periodIndex }.eachCount()

    @Test
    fun `one empty period among full ones is new data, written as sent`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        val before = rowsByPeriod(outbound)
        assertEquals(2, before.size)
        graph.clock.advanceBy(Duration.ofHours(13))
        scheduleWith { pattern, real ->
            if (pattern != outbound) {
                real
            } else {
                real.copy(periods = real.periods.mapIndexed { i, p -> if (i == 0) p.copy(stops = emptyList()) else p })
            }
        }
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        // Some rows arrived for the pattern, so it is not "empty": the cache is not mixed in.
        assertEquals(2, dataDao.getSchedulePeriods(r326.value, outbound.value).size)
        assertEquals(mapOf(1 to before.getValue(1)), rowsByPeriod(outbound))
        val schedule = graph.routes.observeSchedule(r326, outbound).first()
        assertEquals(listOf(0, before.getValue(1)), schedule?.periods?.map { it.stops.size })
    }

    @Test
    fun `an all-empty pattern keeps its cache while the other pattern takes a shorter timetable`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        val outboundBefore = dataDao.getScheduleStopTimes(r326.value, outbound.value).map { it.seq to it.periodIndex }
        val outboundPeriods = dataDao.getSchedulePeriods(r326.value, outbound.value)
        graph.clock.advanceBy(Duration.ofHours(13))
        scheduleWith { pattern, real ->
            when (pattern) {
                // One period instead of two, and with no rows: every cached period must stay.
                outbound -> real.copy(periods = real.periods.take(1).map { it.copy(stops = emptyList()) })

                inbound -> real.copy(periods = real.periods.take(1))

                else -> real
            }
        }
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        assertEquals(outboundPeriods, dataDao.getSchedulePeriods(r326.value, outbound.value))
        assertEquals(
            outboundBefore,
            dataDao.getScheduleStopTimes(r326.value, outbound.value).map { it.seq to it.periodIndex }
        )
        assertEquals(1, dataDao.getSchedulePeriods(r326.value, inbound.value).size)
        assertEquals(setOf(0), rowsByPeriod(inbound).keys)
    }

    @Test
    fun `empty periods on a first sync are written, with nothing to keep`() = runBlocking {
        scheduleWith { _, real -> real.copy(periods = real.periods.map { it.copy(stops = emptyList()) }) }
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        assertEquals(2, dataDao.countPatterns(r326.value))
        assertTrue(dataDao.getScheduleStopTimes(r326.value, outbound.value).isEmpty())
        val schedule = graph.routes.observeSchedule(r326, outbound).first()
        assertTrue(schedule == null || schedule.periods.all { it.stops.isEmpty() })

        // The next good response fills them in.
        healSchedule()
        graph.clock.advanceBy(Duration.ofHours(13))
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        assertTrue(dataDao.getScheduleStopTimes(r326.value, outbound.value).isNotEmpty())
    }

    @Test
    fun `an empty timetable kept from the cache does not survive a real change of patterns`() = runBlocking {
        graph.routes.refreshRouteIfStale(r326)
        graph.clock.advanceBy(Duration.ofHours(13))
        // The detail now lists only the outbound pattern, whose timetable came back empty.
        gateway.routeOverride = { id, language ->
            val real = FixtureDomain.route(id, language)
            real.copy(patterns = real.patterns.filter { it.suffix == outbound })
        }
        scheduleWith { _, real -> real.copy(periods = emptyList()) }
        assertEquals(SyncOutcome.Synced, graph.routes.refreshRouteIfStale(r326))
        assertEquals(1, dataDao.countPatterns(r326.value))
        assertTrue(dataDao.getScheduleStopTimes(r326.value, outbound.value).isNotEmpty())
        // Nothing of the dropped pattern is resurrected.
        assertTrue(dataDao.getScheduleStopTimes(r326.value, inbound.value).isEmpty())
        assertTrue(dataDao.getSchedulePeriods(r326.value, inbound.value).isEmpty())
    }

    private fun healSchedule() {
        gateway.scheduleOverride = null
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
