package ge.hackerman.gza.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.data.database.mergeStops
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.model.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class StopDaoTest : DaoTest() {
    private val dao get() = db.stopDao()

    @Test
    fun `replaceAll swaps the whole set and a reader never sees it empty`() = runTest {
        dao.replaceAll(listOf(stop("1:1"), stop("1:2")), synced("stops"))
        dao.observeAll().test {
            assertEquals(listOf("1:1", "1:2"), awaitItem().map { it.id })
            dao.replaceAll(listOf(stop("1:3")), synced("stops"))
            assertEquals(listOf("1:3"), awaitItem().map { it.id })
            expectNoEvents()
        }
        assertEquals(T0, db.syncStateDao().get("stops")?.syncedAt)
    }

    @Test
    fun `the real catalog stores every stop with both names`() = runTest {
        val rows = mergeStops(FixtureDomain.stops(Language.EN), FixtureDomain.stops(Language.KA))
        dao.replaceAll(rows, synced("stops"))
        assertEquals(2753, dao.count())
        val stop = dao.get(FixtureDomain.STOP_970)!!
        assertEquals("Ana Politkovskaia Street", stop.nameEn)
        assertEquals("ანა პოლიტკოვსკაიას ქუჩა", stop.nameKa)
        assertEquals(listOf(FixtureDomain.STOP_970), dao.getByCode("970").map { it.id })
    }

    @Test
    fun `loadAll reads the rows with their sync time`() = runTest {
        assertEquals(TableSnapshot<StopEntity>(emptyList(), null), dao.loadAll("stops"))
        dao.replaceAll(listOf(stop("1:1")), synced("stops"))
        val snapshot = dao.loadAll("stops")
        assertEquals(listOf("1:1"), snapshot.rows.map { it.id })
        assertEquals(T0, snapshot.syncState?.syncedAt)
    }

    @Test
    fun `observeAll is ordered by id`() = runTest {
        dao.replaceAll(listOf(stop("1:9"), stop("1:10"), stop("1:1")), synced("stops"))
        dao.observeAll().test {
            assertEquals(listOf("1:1", "1:10", "1:9"), awaitItem().map { it.id })
        }
    }

    @Test
    fun `lookups by id and code`() = runTest {
        dao.replaceAll(listOf(stop("1:970"), stop("1:metro_1_1", code = null)), synced("stops"))
        assertEquals("1:970", dao.get("1:970")?.id)
        assertNull(dao.get("1:404"))
        assertEquals(listOf("1:970"), dao.getByCode("970").map { it.id })
        dao.observe("1:metro_1_1").test { assertNull(awaitItem()?.code) }
    }

    @Test
    fun `insertIfAbsent keeps an existing row and its names`() = runTest {
        dao.replaceAll(listOf(stop("1:970", en = "Ana", ka = "ანა")), synced("stops"))
        dao.insertIfAbsent(listOf(stop("1:970", en = "Other", ka = null), stop("1:971")))
        assertEquals("ანა", dao.get("1:970")?.nameKa)
        assertEquals("Ana", dao.get("1:970")?.nameEn)
        assertEquals(2, dao.count())
    }

    @Test
    fun `a failing replace keeps the old set`() = runTest {
        dao.replaceAll(listOf(stop("1:1")), synced("stops", T0))
        // A repeated id violates the primary key half way through the transaction.
        runCatching { dao.replaceAll(listOf(stop("1:2"), stop("1:2")), synced("stops", T0.plusSeconds(60))) }
            .onSuccess { error("expected a constraint failure") }
        assertEquals(listOf("1:1"), dao.getByCode("1").map { it.id })
        assertEquals(1, dao.count())
        assertEquals(T0, db.syncStateDao().get("stops")?.syncedAt)
    }
}
