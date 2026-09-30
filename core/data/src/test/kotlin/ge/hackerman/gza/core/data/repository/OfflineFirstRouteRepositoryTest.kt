package ge.hackerman.gza.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.RouteBundle
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.StopId
import java.time.DayOfWeek
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstRouteRepositoryTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val repository = graph.routes
    private val r326 = FixtureDomain.routeId("326")
    private val r301 = FixtureDomain.routeId("301")
    private val s970 = StopId(FixtureDomain.STOP_970)
    private val outbound = PatternSuffix("0:01")

    @After
    fun tearDown() = graph.db.close()

    private suspend fun bundle(): RouteBundle = (repository.observeRoute(r326).first() as CachedResult.Data).value

    @Test
    fun `a route is loading until its first use syncs it`() = runBlocking {
        repository.observeRoute(r326).test {
            assertEquals(CachedResult.Loading, awaitItem())
            repository.refreshRouteIfStale(r326)
            var item = awaitItem()
            while (item !is CachedResult.Data) item = awaitItem()
            assertEquals(Freshness.FRESH, item.freshness)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the bundle joins stop order with names and coordinates`() = runBlocking {
        graph.catalogSync.syncIfStale()
        repository.refreshRouteIfStale(r326)
        graph.language.language.value = Language.KA
        val bundle = bundle()
        assertEquals("326", bundle.route.shortName)
        assertEquals(listOf("0:01", "1:01"), bundle.detail.patterns.map { it.suffix.value })
        assertNull(bundle.detail.defaultPattern)
        val stops = bundle.patternStops.getValue(outbound).stops
        assertEquals(46, stops.size)
        assertEquals(s970, stops.first().id)
        assertEquals("ანა პოლიტკოვსკაიას ქუჩა", stops.first().name)
        assertEquals("ბარათაშვილის ქ.", bundle.detail.patterns.first { it.suffix == outbound }.headsign)
        assertEquals(41.722055, stops.first().location.lat, 1e-6)
    }

    @Test
    fun `polylines decode to the terminus coordinates`() = runBlocking {
        repository.refreshRouteIfStale(r326)
        val bundle = bundle()
        val points = bundle.polylines.getValue(outbound).encoded.decode()
        val first = bundle.patternStops.getValue(outbound).stops.first().location
        val last = bundle.patternStops.getValue(outbound).stops.last().location
        // Within about 300 m of each terminus.
        assertTrue(abs(points.first().lat - first.lat) < 0.003 && abs(points.first().lon - first.lon) < 0.003)
        assertTrue(abs(points.last().lat - last.lat) < 0.003 && abs(points.last().lon - last.lon) < 0.003)
    }

    @Test
    fun `schedules at 1-970 give one timetable per route and pattern, only that stop's rows`() = runBlocking {
        listOf("301", "326", "551").forEach { repository.refreshRouteIfStale(FixtureDomain.routeId(it)) }
        val schedules = repository.observeSchedulesAtStop(s970).first()
        assertEquals(6, schedules.size)
        schedules.forEach { schedule ->
            assertTrue(schedule.periods.isNotEmpty())
            schedule.periods.forEach { period -> assertTrue(period.stops.all { it.stopId == s970 }) }
        }
        val weekday301 = schedules.first { it.routeId == r301 && it.pattern == outbound }.periods
            .first { it.fromDay == DayOfWeek.MONDAY }
        assertEquals(427, weekday301.stops.single().times.first().minutes)
        // 301 towards 1:970 runs past midnight: those times stay on the service day, above 1440.
        val lateInbound = schedules.first { it.routeId == r301 && it.pattern == PatternSuffix("1:01") }
        assertTrue(lateInbound.periods.flatMap { it.stops }.flatMap { it.times }.any { it.minutes >= 1440 })
    }

    @Test
    fun `observeSchedule gives a whole pattern, and null for an unknown one`() = runBlocking {
        repository.refreshRouteIfStale(r326)
        val schedule = repository.observeSchedule(r326, outbound).first()!!
        assertEquals(2, schedule.periods.size)
        assertEquals(46, schedule.periods.first().stops.size)
        assertEquals(
            schedule,
            FixtureDomain.schedule(r326, outbound).let { real ->
                real.copy(
                    periods = real.periods.map { period ->
                        period.copy(stops = period.stops.map { it.copy(name = null) })
                    }
                )
            }
        )
        assertNull(repository.observeSchedule(r326, PatternSuffix("9:99")).first())
    }

    @Test
    fun `the route catalog follows the language`() = runBlocking {
        graph.catalogSync.syncIfStale()
        val routes = (repository.observeRoutes().first() as CachedResult.Data).value
        assertEquals(280, routes.size)
        graph.language.language.value = Language.KA
        val ka = (repository.observeRoutes().first() as CachedResult.Data).value
        assertTrue(ka.first { it.id == r326 }.longName!!.any { it in 'ა'..'ჰ' })
    }
}
