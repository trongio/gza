package ge.hackerman.gza.core.data.model

import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RoutePolyline

/**
 * A cached route: its patterns, stop order and shapes. [RouteDetail.defaultPattern] is always
 * null here: the gateway flips it during the day, so it is never cached.
 */
data class RouteBundle(
    /** From the catalog; before the catalog has synced, from the route's detail, without a long name. */
    val route: Route,
    val detail: RouteDetail,
    val patternStops: Map<PatternSuffix, PatternStops>,
    val polylines: Map<PatternSuffix, RoutePolyline>
)
