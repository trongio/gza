package ge.hackerman.gza.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstStopRepositoryTest {
    private val graph = DataTestGraph(TestDatabase.inMemory())
    private val repository = graph.stops
    private val s970 = StopId(FixtureDomain.STOP_970)

    @After
    fun tearDown() = graph.db.close()

    private fun CachedResult<List<Stop>>.stops(): List<Stop> = (this as CachedResult.Data).value

    @Test
    fun `loading until the first sync, then fresh data`() = runBlocking {
        repository.observeStops().test {
            assertEquals(CachedResult.Loading, awaitItem())
            graph.catalogSync.syncIfStale()
            var item = awaitItem()
            while (item !is CachedResult.Data) item = awaitItem()
            assertEquals(2753, item.value.size)
            assertEquals(Freshness.FRESH, item.freshness)
            assertEquals(graph.clock.now, item.syncedAt)
            assertNull(item.refreshError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `names follow the language without a new sync`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.language.language.value = Language.KA
        repository.observeStop(s970).test {
            assertEquals("ანა პოლიტკოვსკაიას ქუჩა", awaitItem()?.name)
            graph.gateway.calls.clear()
            graph.language.language.value = Language.EN
            assertEquals("Ana Politkovskaia Street", awaitItem()?.name)
            assertTrue(graph.gateway.calls.isEmpty())
        }
        val all = repository.observeStops().first().stops()
        assertEquals("Ana Politkovskaia Street", all.first { it.id == s970 }.name)
    }

    @Test
    fun `a missing georgian name falls back to english and a nameless stop is dropped`() = runBlocking {
        graph.db.stopDao().replaceAll(
            listOf(
                StopEntity("1:1", "1", "Only English", null, 41.7, 44.7, TransportKind.BUS),
                StopEntity("1:2", "2", null, null, 41.7, 44.7, TransportKind.BUS)
            ),
            SyncStateEntity("stops", graph.clock.now)
        )
        graph.language.language.value = Language.KA
        assertEquals(listOf("Only English"), repository.observeStops().first().stops().map { it.name })
        assertNull(repository.observeStop(StopId("1:2")).first())
    }

    @Test
    fun `stale after a week`() = runBlocking {
        graph.catalogSync.syncIfStale()
        graph.clock.advanceBy(Duration.ofDays(7))
        val result = repository.observeStops().first() as CachedResult.Data
        assertEquals(Freshness.STALE, result.freshness)
    }

    @Test
    fun `an empty cache after a failed sync is unavailable offline`() = runBlocking {
        graph.gateway.failure = TtcGatewayException.Network(IOException())
        graph.catalogSync.syncIfStale()
        assertEquals(CachedResult.Unavailable(SyncError.OFFLINE), repository.observeStops().first())
    }

    @Test
    fun `stop routes load on first use and are then served from the cache`() = runBlocking {
        repository.observeStopRoutes(s970).test {
            assertEquals(CachedResult.Loading, awaitItem())
            repository.refreshStopRoutesIfStale(s970)
            var item = awaitItem()
            while (item !is CachedResult.Data) item = awaitItem()
            assertEquals(setOf("301", "326", "551"), item.value.map { it.shortName }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stop with no routes is data, not loading`() = runBlocking {
        graph.gateway.stopRoutesOverride = { emptyList() }
        repository.refreshStopRoutesIfStale(StopId("1:gondola_5"))
        val result = repository.observeStopRoutes(StopId("1:gondola_5")).first() as CachedResult.Data
        assertEquals(emptyList<Any>(), result.value)
    }
}
