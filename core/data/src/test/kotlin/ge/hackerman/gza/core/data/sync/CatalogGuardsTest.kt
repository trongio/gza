package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.Language
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The shrink and empty guards at their exact edges: 50% of the cache and of the English list. */
@RunWith(AndroidJUnit4::class)
class CatalogGuardsTest {
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

    @Test
    fun `exactly half of the cached routes is accepted, one fewer is rejected`() = runBlocking {
        syncOnceThenAge()
        assertEquals(280, db.routeDao().count())
        gateway.routesOverride = { FixtureDomain.routes(it).take(139) }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(280, db.routeDao().count())
        gateway.routesOverride = { FixtureDomain.routes(it).take(140) }
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals(140, db.routeDao().count())
    }

    @Test
    fun `half of an odd cache rounds up - 1376 of 2753 stops is rejected, 1377 accepted`() = runBlocking {
        syncOnceThenAge()
        gateway.stopsOverride = { FixtureDomain.stops(it).take(1376) }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(2753, db.stopDao().count())
        gateway.stopsOverride = { FixtureDomain.stops(it).take(1377) }
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals(1377, db.stopDao().count())
    }

    @Test
    fun `a rejected list leaves the sync time alone, so the next call tries again`() = runBlocking {
        syncOnceThenAge()
        val before = db.syncStateDao().get(SyncKey.Stops.value)
        gateway.stopsOverride = { emptyList() }
        sync.syncIfStale()
        assertEquals(before, db.syncStateDao().get(SyncKey.Stops.value))
        gateway.calls.clear()
        sync.syncIfStale()
        assertEquals(2, gateway.callsTo("stops").size)
    }

    @Test
    fun `an empty english route list on the very first sync writes nothing`() = runBlocking {
        gateway.routesOverride = { emptyList() }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(0, db.routeDao().count())
        assertNull(db.syncStateDao().get(SyncKey.Routes.value))
        assertEquals(2753, db.stopDao().count())
    }

    @Test
    fun `a full size list of repeated ids counts as the ids it really has`() = runBlocking {
        syncOnceThenAge()
        gateway.stopsOverride = { language ->
            val some = FixtureDomain.stops(language).take(1000)
            List(2753) { some[it % some.size] }
        }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(2753, db.stopDao().count())
    }

    @Test
    fun `a georgian list of exactly half the english one is accepted, one fewer is rejected`() = runBlocking {
        gateway.routesOverride = { language ->
            val all = FixtureDomain.routes(language)
            if (language == Language.KA) all.take(139) else all
        }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(0, db.routeDao().count())

        gateway.routesOverride = { language ->
            val all = FixtureDomain.routes(language)
            if (language == Language.KA) all.take(140) else all
        }
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        val rows = db.routeDao().getAll()
        assertEquals(280, rows.size)
        assertEquals(140, rows.count { it.longNameKa != null })
    }

    @Test
    fun `a shrunk georgian list never wipes the cached georgian names`() = runBlocking {
        syncOnceThenAge()
        val kaBefore = db.stopDao().get(FixtureDomain.STOP_970)?.nameKa
        val namedBefore = db.stopDao().getAll().count { it.nameKa != null }
        gateway.stopsOverride = { language ->
            val all = FixtureDomain.stops(language)
            if (language == Language.KA) all.take(all.size / 2 - 1) else all
        }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(kaBefore, db.stopDao().get(FixtureDomain.STOP_970)?.nameKa)
        assertEquals(namedBefore, db.stopDao().getAll().count { it.nameKa != null })
    }
}
