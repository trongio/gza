package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.FakeTtcGatewayClient
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.MutableClock
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.StopId
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StopRoutesSyncTest {
    private val db: GzaDatabase = TestDatabase.inMemory()
    private val gateway = FakeTtcGatewayClient()
    private val clock = MutableClock()
    private val sync = StopRoutesSync(gateway, db, SyncStatusTracker(clock), StalenessPolicy(), clock)
    private val s970 = StopId(FixtureDomain.STOP_970)
    private val expected = listOf("301", "326", "551").map { FixtureDomain.routeId(it).value }.sorted()

    @After
    fun tearDown() = db.close()

    @Test
    fun `1-970 is served by 301, 326 and 551, inserted even without the catalog`() = runBlocking {
        assertEquals(0, db.routeDao().count())
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(s970))
        assertEquals(expected, db.stopRoutesDao().getRouteIds(s970.value))
        val routes = db.stopRoutesDao().observeRoutes(s970.value).first()
        assertEquals(expected, routes.map { it.id })
        assertEquals(setOf("301", "326", "551"), routes.map { it.shortName }.toSet())
    }

    @Test
    fun `fresh for a week, then refetched`() = runBlocking {
        sync.syncIfStale(s970)
        clock.advanceBy(Duration.ofDays(6))
        assertEquals(SyncOutcome.UpToDate, sync.syncIfStale(s970))
        clock.advanceBy(Duration.ofDays(1))
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(s970))
        assertEquals(2, gateway.calls.size)
    }

    @Test
    fun `an empty response keeps the cached rows`() = runBlocking {
        sync.syncIfStale(s970)
        clock.advanceBy(Duration.ofDays(8))
        gateway.stopRoutesOverride = { emptyList() }
        assertEquals(SyncOutcome.Failed(SyncError.EMPTY_OR_SHRUNK), sync.syncIfStale(s970))
        assertEquals(expected, db.stopRoutesDao().getRouteIds(s970.value))
    }

    @Test
    fun `a stop with no routes on its first sync is stored as such`() = runBlocking {
        gateway.stopRoutesOverride = { emptyList() }
        assertEquals(SyncOutcome.Synced, sync.syncIfStale(StopId("1:gondola_5")))
        assertTrue(db.stopRoutesDao().getRouteIds("1:gondola_5").isEmpty())
    }
}
