package ge.hackerman.gza.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.model.CachedResult
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.model.SyncError
import ge.hackerman.gza.core.data.model.SyncOutcome
import ge.hackerman.gza.core.data.testing.DataTestGraph
import ge.hackerman.gza.core.data.testing.FakeTtcGatewayClient
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.MutableClock
import ge.hackerman.gza.core.data.testing.TestDataStores
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.SavedStop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayException
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The backlog's offline promise: after one online launch, the app keeps working with the
 * gateway gone. A file database closed and reopened stands in for process death.
 */
@RunWith(AndroidJUnit4::class)
class OfflineAfterOnlineLaunchTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = MutableClock()
    private val s970 = StopId(FixtureDomain.STOP_970)
    private val usedRoutes = listOf("301", "326", "551").map { FixtureDomain.routeId(it) }

    private fun graph(dbFile: File, gateway: FakeTtcGatewayClient = FakeTtcGatewayClient()) =
        DataTestGraph(TestDatabase.onFile(dbFile), gateway, clock)

    /** Launch 1, online: what the Now screen needs for stop 1:970, plus a saved stop. */
    private fun onlineLaunch(dbFile: File, stores: TestDataStores) = runBlocking {
        val online = graph(dbFile)
        assertEquals(SyncOutcome.Synced, online.catalogSync.syncIfStale())
        assertEquals(SyncOutcome.Synced, online.stops.refreshStopRoutesIfStale(s970))
        usedRoutes.forEach { assertEquals(SyncOutcome.Synced, online.routes.refreshRouteIfStale(it)) }
        DataStoreUserPreferencesRepository(stores.userPreferences()).saveStop(s970, 4)
        online.db.close()
        stores.closeAll()
    }

    @Test
    fun `every cached flow serves full data with an offline hint when the gateway throws`() = runBlocking {
        val dbFile = folder.newFile("gza.db").also { it.delete() }
        val stores = TestDataStores(folder.newFolder("files"))
        onlineLaunch(dbFile, stores)

        clock.advanceBy(Duration.ofDays(8))
        val offlineGateway = FakeTtcGatewayClient().apply { failure = TtcGatewayException.Network(IOException()) }
        val offline = graph(dbFile, offlineGateway)
        try {
            // The app's triggers all try, and all fail.
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), offline.catalogSync.syncIfStale())
            assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), offline.stops.refreshStopRoutesIfStale(s970))
            usedRoutes.forEach {
                assertEquals(SyncOutcome.Failed(SyncError.OFFLINE), offline.routes.refreshRouteIfStale(it))
            }
            assertTrue(offlineGateway.calls.isNotEmpty())

            val stops = offline.stops.observeStops().first().assertStaleOffline()
            assertEquals(2753, stops.size)
            assertEquals(3, offline.stops.observeStopRoutes(s970).first().assertStaleOffline().size)
            assertEquals(280, offline.routes.observeRoutes().first().assertStaleOffline().size)
            usedRoutes.forEach { id ->
                val bundle = offline.routes.observeRoute(id).first().assertStaleOffline()
                assertEquals(2, bundle.detail.patterns.size)
                assertEquals(2, bundle.polylines.size)
                assertTrue(bundle.patternStops.values.all { it.stops.isNotEmpty() })
            }
            val atStop = offline.routes.observeSchedulesAtStop(s970).first()
            assertEquals(6, atStop.size)
            assertEquals(
                427,
                atStop.first { it.routeId == usedRoutes[0] && it.pattern == PatternSuffix("0:01") }
                    .periods.first().stops.single().times.first().minutes
            )
            assertEquals("Ana Politkovskaia Street", offline.stops.observeStop(s970).first()?.name)

            val prefs = DataStoreUserPreferencesRepository(stores.userPreferences())
            assertEquals(listOf(SavedStop(s970, 4, emptySet())), prefs.savedStops.first())
        } finally {
            offline.db.close()
            stores.closeAll()
        }
    }

    @Test
    fun `a dead key still serves the cache, marked no key`() = runBlocking {
        val dbFile = folder.newFile("gza.db").also { it.delete() }
        val stores = TestDataStores(folder.newFolder("files"))
        onlineLaunch(dbFile, stores)
        clock.advanceBy(Duration.ofDays(8))
        val gateway = FakeTtcGatewayClient().apply { failure = TtcGatewayException.Http(401, null) }
        val noKey = graph(dbFile, gateway)
        try {
            noKey.catalogSync.syncIfStale()
            noKey.routes.refreshRouteIfStale(usedRoutes[1])
            val stops = noKey.stops.observeStops().first() as CachedResult.Data
            assertEquals(SyncError.NO_KEY, stops.refreshError)
            assertEquals(2753, stops.value.size)
            val route = noKey.routes.observeRoute(usedRoutes[1]).first() as CachedResult.Data
            assertEquals(SyncError.NO_KEY, route.refreshError)
        } finally {
            noKey.db.close()
            stores.closeAll()
        }
    }

    @Test
    fun `a fresh install that is offline says so and is never loading forever`() = runBlocking {
        val gateway = FakeTtcGatewayClient().apply { failure = TtcGatewayException.Network(IOException()) }
        val offline = graph(folder.newFile("fresh.db").also { it.delete() }, gateway)
        try {
            offline.catalogSync.syncIfStale()
            offline.routes.refreshRouteIfStale(usedRoutes[0])
            offline.stops.refreshStopRoutesIfStale(s970)
            assertEquals(CachedResult.Unavailable(SyncError.OFFLINE), offline.stops.observeStops().first())
            assertEquals(CachedResult.Unavailable(SyncError.OFFLINE), offline.routes.observeRoutes().first())
            assertEquals(
                CachedResult.Unavailable(SyncError.OFFLINE),
                offline.routes.observeRoute(usedRoutes[0]).first()
            )
            assertEquals(CachedResult.Unavailable(SyncError.OFFLINE), offline.stops.observeStopRoutes(s970).first())
            assertEquals(emptyList<Any>(), offline.routes.observeSchedulesAtStop(s970).first())
        } finally {
            offline.db.close()
        }
    }

    private fun <T> CachedResult<T>.assertStaleOffline(): T {
        val data = this as CachedResult.Data
        assertEquals(Freshness.STALE, data.freshness)
        assertEquals(SyncError.OFFLINE, data.refreshError)
        return data.value
    }
}
