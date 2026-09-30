package ge.hackerman.gza.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.database.entity.StopRouteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class StopRoutesDaoTest : DaoTest() {
    private val dao get() = db.stopRoutesDao()

    private fun rows(stop: String, vararg routes: String) = routes.map { StopRouteEntity(stop, it) }

    @Test
    fun `replaceForStop replaces one stop and inserts missing routes`() = runTest {
        db.routeDao().replaceAll(listOf(route("1:A", en = "Catalog")), synced("routes"))
        dao.replaceForStop(
            "1:970",
            rows("1:970", "1:A", "1:B"),
            listOf(route("1:A", en = null), route("1:B")),
            synced("k")
        )
        dao.replaceForStop("1:972", rows("1:972", "1:A"), listOf(route("1:A")), synced("k2"))
        dao.observeRoutes("1:970").test {
            val routes = awaitItem()
            assertEquals(listOf("1:A", "1:B"), routes.map { it.id })
            assertEquals("Catalog", routes.first().longNameEn)
            dao.replaceForStop("1:970", rows("1:970", "1:B"), emptyList(), synced("k"))
            assertEquals(listOf("1:B"), awaitItem().map { it.id })
        }
        assertEquals(listOf("1:A"), dao.getRouteIds("1:972"))
    }
}
