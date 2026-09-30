package ge.hackerman.gza.core.data.testing

import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.language.ContentLanguage
import ge.hackerman.gza.core.data.repository.OfflineFirstRouteRepository
import ge.hackerman.gza.core.data.repository.OfflineFirstStopRepository
import ge.hackerman.gza.core.data.sync.CatalogSync
import ge.hackerman.gza.core.data.sync.RouteSync
import ge.hackerman.gza.core.data.sync.StalenessPolicy
import ge.hackerman.gza.core.data.sync.StopRoutesSync
import ge.hackerman.gza.core.data.sync.SyncStatusTracker
import ge.hackerman.gza.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeContentLanguage(initial: Language = Language.EN) : ContentLanguage {
    override val language = MutableStateFlow(initial)
    var refreshes = 0

    override fun refresh() {
        refreshes++
    }
}

/** The data layer wired by hand, the way Hilt wires it, on one database. */
internal class DataTestGraph(
    val db: GzaDatabase,
    val gateway: FakeTtcGatewayClient = FakeTtcGatewayClient(),
    val clock: MutableClock = MutableClock(),
    val language: FakeContentLanguage = FakeContentLanguage()
) {
    val policy = StalenessPolicy()
    val tracker = SyncStatusTracker(clock)
    val catalogSync = CatalogSync(gateway, db, tracker, policy, clock)
    val routeSync = RouteSync(gateway, db, tracker, policy, clock)
    val stopRoutesSync = StopRoutesSync(gateway, db, tracker, policy, clock)
    val stops = OfflineFirstStopRepository(db, stopRoutesSync, tracker, policy, clock, language, Dispatchers.Default)
    val routes = OfflineFirstRouteRepository(db, routeSync, tracker, policy, clock, language, Dispatchers.Default)
}
