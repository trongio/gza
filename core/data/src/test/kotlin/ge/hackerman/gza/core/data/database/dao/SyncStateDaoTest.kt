package ge.hackerman.gza.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SyncStateDaoTest : DaoTest() {
    private val dao get() = db.syncStateDao()

    @Test
    fun `upsert and observe`() = runTest {
        dao.observe("stops").test {
            assertNull(awaitItem())
            dao.upsert(SyncStateEntity("stops", T0))
            assertEquals(T0, awaitItem()?.syncedAt)
            dao.upsert(SyncStateEntity("stops", T0.plusSeconds(1)))
            assertEquals(T0.plusSeconds(1), awaitItem()?.syncedAt)
        }
    }

    @Test
    fun `markUsed only touches an existing row`() = runTest {
        assertEquals(0, dao.markUsed("route:1:R", T0))
        assertNull(dao.get("route:1:R"))
        dao.upsert(SyncStateEntity("route:1:R", T0))
        assertEquals(1, dao.markUsed("route:1:R", T0.plusSeconds(5)))
        assertEquals(T0.plusSeconds(5), dao.get("route:1:R")?.lastUsedAt)
        assertEquals(T0, dao.get("route:1:R")?.syncedAt)
    }

    @Test
    fun `getUsedSince filters by prefix and last use`() = runTest {
        val old = T0 - Duration.ofDays(20)
        dao.upsert(SyncStateEntity("route:1:A", T0, lastUsedAt = T0))
        dao.upsert(SyncStateEntity("route:1:B", T0, lastUsedAt = old))
        dao.upsert(SyncStateEntity("route:1:C", T0, lastUsedAt = null))
        dao.upsert(SyncStateEntity("stop-routes:1:970", T0, lastUsedAt = T0))
        val since = T0 - Duration.ofDays(14)
        assertEquals(listOf("route:1:A"), dao.getUsedSince("route:", since).map { it.key })
    }
}
