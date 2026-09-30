package ge.hackerman.gza.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.database.mergeRoutes
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.TransportKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class RouteDaoTest : DaoTest() {
    private val dao get() = db.routeDao()

    @Test
    fun `replaceAll swaps the whole set and a reader never sees it empty`() = runTest {
        dao.replaceAll(listOf(route("1:A"), route("1:B")), synced("routes"))
        dao.observeAll().test {
            assertEquals(listOf("1:A", "1:B"), awaitItem().map { it.id })
            dao.replaceAll(listOf(route("1:C")), synced("routes"))
            assertEquals(listOf("1:C"), awaitItem().map { it.id })
            expectNoEvents()
        }
    }

    @Test
    fun `the real catalog stores every route with both long names`() = runTest {
        dao.replaceAll(
            mergeRoutes(FixtureDomain.routes(Language.EN), FixtureDomain.routes(Language.KA)),
            synced("routes")
        )
        assertEquals(280, dao.count())
        val r326 = dao.get(FixtureDomain.routeId("326").value)!!
        assertEquals("326", r326.shortName)
        assertEquals(0x00B38B, r326.color)
        assertEquals(TransportKind.BUS, r326.kind)
        assertEquals(true, r326.longNameEn?.contains("Politkovskaya"))
        assertEquals(true, r326.longNameKa?.any { it in 'ა'..'ჰ' })
        assertEquals(TransportKind.MINIBUS, dao.get(FixtureDomain.routeId("551").value)?.kind)
    }

    @Test
    fun `insertIfAbsent keeps the catalog row`() = runTest {
        dao.replaceAll(listOf(route("1:A", en = "Catalog")), synced("routes"))
        dao.insertIfAbsent(listOf(route("1:A", en = null), route("1:B")))
        assertEquals("Catalog", dao.get("1:A")?.longNameEn)
        assertEquals(2, dao.count())
        dao.observe("1:Z").test { assertNull(awaitItem()) }
    }
}
