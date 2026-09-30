package ge.hackerman.gza.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.entity.PatternStopEntity
import ge.hackerman.gza.core.data.database.routeDataRows
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.RouteId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class RouteDataDaoTest : DaoTest() {
    private val dao get() = db.routeDataDao()

    private fun rowsFor(name: String): RouteDataRows {
        val id = FixtureDomain.routeId(name)
        val suffixes = FixtureDomain.suffixes(id)
        return routeDataRows(
            detailEn = FixtureDomain.route(id, Language.EN),
            detailKa = FixtureDomain.route(id, Language.KA),
            patternStops = suffixes.map { FixtureDomain.patternStops(id, it) },
            schedules = suffixes.map { FixtureDomain.schedule(id, it) },
            polylines = FixtureDomain.polylines(id)
        )
    }

    private suspend fun store(name: String, rows: RouteDataRows = rowsFor(name)) {
        val id = FixtureDomain.routeId(name).value
        dao.replaceRouteData(id, rows, synced("route:$id"))
    }

    private suspend fun rowCounts(routeId: RouteId): List<Int> {
        val suffixes = dao.getPatterns(routeId.value).map { it.suffix }
        return listOf(
            dao.countPatterns(routeId.value),
            suffixes.sumOf { dao.getPatternStops(routeId.value, it).size },
            dao.getPolylines(routeId.value).size,
            suffixes.sumOf { dao.getSchedulePeriods(routeId.value, it).size },
            suffixes.sumOf { dao.getScheduleStopTimes(routeId.value, it).size }
        )
    }

    @Test
    fun `route 326 from the fixtures is stored in travel order`() = runTest {
        store("326")
        val id = FixtureDomain.routeId("326").value
        assertEquals(listOf("0:01", "1:01"), dao.getPatterns(id).map { it.suffix })
        val outbound = dao.getPatternStops(id, "0:01")
        assertEquals(46, outbound.size)
        assertEquals((0 until 46).toList(), outbound.map { it.seq })
        assertEquals("1:970", outbound.first().stopId)
        assertEquals("1:824", outbound.last().stopId)
        assertEquals(2, dao.getPolylines(id).size)
        assertEquals(2, dao.getSchedulePeriods(id, "0:01").size)
        assertEquals(2, dao.getSchedulePeriods(id, "1:01").size)
        val pattern = dao.getPatterns(id).first { it.suffix == "0:01" }
        assertEquals("Baratashvili St", pattern.headsignEn)
        assertEquals("ბარათაშვილის ქ.", pattern.headsignKa)
        assertEquals("1:970", pattern.firstStopId)
    }

    @Test
    fun `stops and route from the responses are added only when missing`() = runTest {
        db.stopDao().replaceAll(listOf(stop("1:970", en = "Catalog", ka = "კატალოგი")), synced("stops"))
        store("326")
        assertEquals("კატალოგი", db.stopDao().get("1:970")?.nameKa)
        val added = db.stopDao().get("1:824")!!
        assertEquals("Nikoloz Baratashvili Street", added.nameEn)
        assertEquals("326", db.routeDao().get(FixtureDomain.routeId("326").value)?.shortName)
    }

    @Test
    fun `replacing again leaves nothing of the old version and other routes alone`() = runTest {
        store("326")
        store("551")
        val r326 = FixtureDomain.routeId("326")
        val r551 = FixtureDomain.routeId("551")
        val before551 = rowCounts(r551)
        val full = rowsFor("326")
        val onlyOutbound = full.copy(
            patterns = full.patterns.filter { it.suffix == "0:01" },
            patternStops = full.patternStops.filter { it.suffix == "0:01" }.take(3),
            polylines = full.polylines.filter { it.suffix == "0:01" },
            periods = full.periods.filter { it.suffix == "0:01" && it.periodIndex == 0 },
            stopTimes = full.stopTimes.filter { it.suffix == "0:01" && it.periodIndex == 0 }.take(3)
        )
        store("326", onlyOutbound)
        assertEquals(listOf(1, 3, 1, 1, 3), rowCounts(r326))
        assertEquals(before551, rowCounts(r551))
    }

    @Test
    fun `a failure half way through keeps the previous data intact`() = runTest {
        store("326")
        val id = FixtureDomain.routeId("326")
        val before = rowCounts(id)
        val full = rowsFor("326")
        // A repeated primary key makes the pattern stop insert fail after the deletes ran.
        val broken = full.copy(patternStops = full.patternStops + PatternStopEntity(id.value, "0:01", 0, "1:1"))
        val result = runCatching { store("326", broken) }
        assertTrue(result.isFailure)
        assertEquals(before, rowCounts(id))
        assertEquals("1:970", dao.getPatternStops(id.value, "0:01").first().stopId)
    }

    @Test
    fun `loadRouteData joins pattern stops with stop names and keeps unknown stops`() = runTest {
        store("326")
        db.stopDao().replaceAll(listOf(stop("1:970", en = "Ana", ka = "ანა")), synced("stops"))
        val snapshot = dao.loadRouteData(FixtureDomain.routeId("326").value)
        assertEquals("326", snapshot.route?.shortName)
        assertEquals(46 + 45, snapshot.patternStops.size)
        val first = snapshot.patternStops.first()
        assertEquals("1:970", first.stopId)
        assertEquals("ანა", first.nameKa)
        // Replacing the catalog dropped 1:969: the row stays, with no stop columns.
        val orphan = snapshot.patternStops.first { it.stopId == "1:969" }
        assertEquals(null, orphan.nameEn)
        assertEquals(null, orphan.lat)
    }
}
