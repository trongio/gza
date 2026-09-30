package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.data.testing.failInsertOf
import ge.hackerman.gza.core.data.testing.stopFailingInsertsInto
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A catalog sync that fails or stops half way never leaves a mix of old and new, or nothing. */
@RunWith(AndroidJUnit4::class)
class CatalogAtomicityTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val gateway = graph.gateway
    private val sync = graph.catalogSync
    private val db = graph.db

    @After
    fun tearDown() = db.close()

    private suspend fun syncOnceThenAge() {
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        graph.clock.advanceBy(Duration.ofDays(8))
    }

    private fun renameEverything() {
        gateway.stopsOverride = { language -> FixtureDomain.stops(language).map { it.copy(name = it.name + " v2") } }
        gateway.routesOverride = { language ->
            FixtureDomain.routes(language).map { it.copy(longName = it.longName?.plus(" v2")) }
        }
    }

    @Test
    fun `a storage failure half way through the stop insert keeps every old stop and its sync time`() = runBlocking {
        syncOnceThenAge()
        val stopsBefore = db.stopDao().getAll()
        val syncedBefore = db.syncStateDao().get(SyncKey.Stops.value)
        renameEverything()
        // The last stop by id: the delete and most inserts have already run when it fails.
        db.failInsertOf("stops", stopsBefore.last().id)

        assertEquals(SyncOutcome.Failed(SyncError.STORAGE), sync.syncIfStale())
        assertEquals(stopsBefore, db.stopDao().getAll())
        assertEquals(syncedBefore, db.syncStateDao().get(SyncKey.Stops.value))
        assertEquals(SyncError.STORAGE, graph.tracker.current(SyncKey.Stops).lastError)
        // Routes are independent and did refresh.
        assertTrue(db.routeDao().getAll().all { it.longNameEn == null || it.longNameEn!!.endsWith(" v2") })
        assertEquals(graph.clock.now, db.syncStateDao().get(SyncKey.Routes.value)?.syncedAt)
    }

    @Test
    fun `a storage failure in the route insert keeps every old route`() = runBlocking {
        syncOnceThenAge()
        val routesBefore = db.routeDao().getAll()
        renameEverything()
        db.failInsertOf("routes", routesBefore[routesBefore.size / 2].id)
        assertEquals(SyncOutcome.Failed(SyncError.STORAGE), sync.syncIfStale())
        assertEquals(routesBefore, db.routeDao().getAll())
        assertTrue(db.stopDao().getAll().all { it.nameEn == null || it.nameEn!!.endsWith(" v2") })

        db.stopFailingInsertsInto("routes")
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertTrue(db.routeDao().getAll().any { it.longNameEn?.endsWith(" v2") == true })
        assertNull(graph.tracker.current(SyncKey.Routes).lastError)
    }

    @Test
    fun `a sync cancelled while fetching writes nothing and does not look in flight or failed`() = runBlocking {
        syncOnceThenAge()
        val stopsBefore = db.stopDao().getAll()
        renameEverything()
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        gateway.calls.clear()
        val running = async { sync.syncIfStale() }
        while (gateway.calls.size < 4) yield()
        assertTrue(graph.tracker.current(SyncKey.Stops).inFlight)
        running.cancel()
        runCatching { running.await() }
        assertEquals(stopsBefore, db.stopDao().getAll())
        val status = graph.tracker.current(SyncKey.Stops)
        assertFalse(status.inFlight)
        assertNull(status.lastError)

        // The lock was released: the next call syncs.
        gateway.gate = null
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals("Ana Politkovskaia Street v2", db.stopDao().get(FixtureDomain.STOP_970)?.nameEn)
    }

    @Test
    fun `a screen watching the stops sees the old set or the new one, never a partial or empty list`() = runBlocking {
        syncOnceThenAge()
        gateway.stopsOverride = { FixtureDomain.stops(it).take(1400) }
        graph.stops.observeStops().test {
            assertEquals(2753, (awaitItem() as CachedResult.Data).value.size)
            val syncing = async { sync.syncIfStale() }
            var item = awaitItem()
            while (true) {
                val size = (item as CachedResult.Data).value.size
                assertTrue("saw $size stops", size == 2753 || size == 1400)
                if (size == 1400) break
                item = awaitItem()
            }
            assertEquals(SyncOutcome.Synced, syncing.await())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
