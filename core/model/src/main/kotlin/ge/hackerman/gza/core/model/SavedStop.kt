package ge.hackerman.gza.core.model

/**
 * A stop the user saved, with how long they walk to it and which routes they care about
 * there. An empty [routeFilter] means every route at the stop, so a route that starts
 * serving it later shows up without the user doing anything.
 */
data class SavedStop(val stopId: StopId, val walkMinutes: Int, val routeFilter: Set<RouteId>) {
    init {
        require(walkMinutes in WALK_MINUTES_RANGE) { "walk minutes out of range: $walkMinutes" }
    }

    fun shows(route: RouteId): Boolean = routeFilter.isEmpty() || route in routeFilter

    companion object {
        /** Longer than an hour is not a walk to a bus stop. */
        val WALK_MINUTES_RANGE: IntRange = 0..60
    }
}
