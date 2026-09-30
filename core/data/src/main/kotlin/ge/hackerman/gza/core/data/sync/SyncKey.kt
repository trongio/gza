package ge.hackerman.gza.core.data.sync

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId

/** One independently synced piece of the cache; [value] is its `sync_state.key`. */
internal sealed class SyncKey(val value: String) {
    data object Stops : SyncKey("stops")

    data object Routes : SyncKey("routes")

    data class Route(val id: RouteId) : SyncKey(ROUTE_PREFIX + id.value)

    data class StopRoutes(val id: StopId) : SyncKey(STOP_ROUTES_PREFIX + id.value)

    final override fun toString(): String = value

    companion object {
        const val ROUTE_PREFIX: String = "route:"
        const val STOP_ROUTES_PREFIX: String = "stop-routes:"
    }
}
