package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.FakeTtcGatewayClient
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.MutableClock
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogSyncTest {
    private val db: GzaDatabase = TestDatabase.inMemory()
    private val gateway = FakeTtcGatewayClient()
    private val clock = MutableClock()
    private val tracker = SyncStatusTracker(clock)
    private val sync = CatalogSync(gateway, db, tracker, StalenessPolicy(), clock)

    @After
    fun tearDown() = db.close()

    private suspend fun stopCount() = db.stopDao().count()

    private suspend fun routeCount() = db.routeDao().count()

    private fun stop(id: String) = Stop(StopId(id), null, "Stop $id", LatLon(41.7, 44.7), TransportKind.BUS)

    @Test
    fun `first sync writes every stop and route with both names`() = runBlocking {
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals(2753, stopCount())
        assertEquals(280, routeCount())
        val s970 = db.stopDao().get(FixtureDomain.STOP_970)!!
        assertEquals("Ana Politkovskaia Street", s970.nameEn)
        assertEquals("ანა პოლიტკოვსკაიას ქუჩა", s970.nameKa)
        assertEquals(clock.now, db.syncStateDao().get("stops")?.syncedAt)
        assertEquals(clock.now, db.syncStateDao().get("routes")?.syncedAt)
        assertEquals(setOf("stops EN", "stops KA", "routes EN", "routes KA"), gateway.calls.toSet())
    }

    @Test
    fun `within a week nothing is fetched, after a week it syncs again`() = runBlocking {
        sync.syncIfStale()
        gateway.calls.clear()
        clock.advanceBy(Duration.ofDays(6))
        assertEquals(SyncOutcome.UpToDate, sync.syncIfStale())
        assertTrue(gateway.calls.isEmpty())
        clock.advanceBy(Duration.ofDays(1))
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals(4, gateway.calls.size)
        assertEquals(clock.now, db.syncStateDao().get("stops")?.syncedAt)
    }

    @Test
    fun `a shorter max age refreshes sooner`() = runBlocking {
        sync.syncIfStale()
        clock.advanceBy(Duration.ofDays(6))
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(maxAge = Duration.ofDays(6)))
    }

    @Test
    fun `an empty stops list never replaces the cached stops`() = runBlocking {
        sync.syncIfStale()
        clock.advanceBy(Duration.ofDays(8))
        gateway.stopsOverride = { emptyList() }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(2753, stopCount())
        assertEquals(SyncError.EMPTY_OR_SHRUNK, tracker.current(SyncKey.Stops).lastError)
        // Routes were fine and did refresh.
        assertEquals(clock.now, db.syncStateDao().get("routes")?.syncedAt)
    }

    @Test
    fun `a list below half the cache is rejected`() = runBlocking {
        sync.syncIfStale()
        clock.advanceBy(Duration.ofDays(8))
        gateway.stopsOverride = { FixtureDomain.stops(it).take(1000) }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(2753, stopCount())
        gateway.stopsOverride = { FixtureDomain.stops(it).take(1400) }
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals(1400, stopCount())
    }

    @Test
    fun `a malformed response leaves both tables unchanged`() = runBlocking {
        sync.syncIfStale()
        clock.advanceBy(Duration.ofDays(8))
        gateway.failure = TtcGatewayException.Malformed(null)
        assertEquals(SyncOutcome.Failed(SyncError.MALFORMED), sync.syncIfStale())
        assertEquals(2753, stopCount())
        assertEquals(280, routeCount())
        assertEquals(clock.now - Duration.ofDays(8), db.syncStateDao().get("stops")?.syncedAt)
    }

    @Test
    fun `georgian failing while english succeeds writes no stops`() = runBlocking {
        gateway.stopsOverride =
            { if (it == Language.KA) throw TtcGatewayException.Http(500, null) else FixtureDomain.stops(it) }
        assertEquals(SyncOutcome.Failed(SyncError.SERVER), sync.syncIfStale())
        assertEquals(0, stopCount())
        assertEquals(280, routeCount())
        assertNull(db.syncStateDao().get("stops"))
    }

    @Test
    fun `a nearly empty georgian list is rejected so it cannot wipe the georgian names`() = runBlocking {
        gateway.stopsOverride = { if (it == Language.KA) emptyList() else FixtureDomain.stops(it) }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale())
        assertEquals(0, stopCount())
    }

    @Test
    fun `routes are written even when stops fail`() = runBlocking {
        gateway.stopsOverride = { throw TtcGatewayException.Network(IOException()) }
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), sync.syncIfStale())
        assertEquals(280, routeCount())
        assertEquals(0, stopCount())
        assertEquals(SyncError.OFFLINE, tracker.current(SyncKey.Stops).lastError)
        assertNull(tracker.current(SyncKey.Routes).lastError)
    }

    @Test
    fun `english is the id list of record`() = runBlocking {
        gateway.stopsOverride = { language ->
            val base = FixtureDomain.stops(language)
            if (language ==
                Language.KA
            ) {
                base.filterNot { it.id.value == FixtureDomain.STOP_970 } + stop("1:only-ka")
            } else {
                base
            }
        }
        sync.syncIfStale()
        assertNull(db.stopDao().get("1:only-ka"))
        val s970 = db.stopDao().get(FixtureDomain.STOP_970)!!
        assertEquals("Ana Politkovskaia Street", s970.nameEn)
        assertNull(s970.nameKa)
    }

    @Test
    fun `two parallel calls make one set of requests`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        val first = async { sync.syncIfStale() }
        val second = async { sync.syncIfStale() }
        while (gateway.calls.size < 4) yield()
        gate.complete(Unit)
        assertEquals(setOf(SyncOutcome.Synced, SyncOutcome.UpToDate), setOf(first.await(), second.await()))
        assertEquals(4, gateway.calls.size)
    }

    @Test
    fun `the tracker shows the sync in flight, then the error`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        gateway.gate = gate
        gateway.failure = TtcGatewayException.Network(IOException())
        val running = async { sync.syncIfStale() }
        while (gateway.calls.size < 4) yield()
        assertTrue(tracker.current(SyncKey.Stops).inFlight)
        assertTrue(tracker.current(SyncKey.Routes).inFlight)
        gate.complete(Unit)
        running.await()
        assertEquals(KeyStatus(false, SyncError.OFFLINE, clock.now, 1), tracker.current(SyncKey.Stops))
    }

    @Test
    fun `app open respects the error backoff, the worker does not`() = runBlocking {
        gateway.failure = TtcGatewayException.Network(IOException())
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), sync.syncIfStale(respectBackoff = true))
        gateway.failure = null
        gateway.calls.clear()
        clock.advanceBy(Duration.ofMinutes(4))
        assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), sync.syncIfStale(respectBackoff = true))
        assertTrue(gateway.calls.isEmpty())
        assertEquals(SyncOutcome.Synced, sync.syncIfStale())
        assertEquals(2753, stopCount())
    }
}
