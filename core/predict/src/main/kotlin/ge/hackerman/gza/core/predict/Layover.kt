package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.VehiclePosition

/**
 * Between trips the gateway sends neither a heading nor a next stop (every parked fix in the
 * fixtures). A heading without a next stop is a bus still pulling in at a last stop, so both
 * must be null. The gateway sends no speed, so there is nothing else to go on.
 */
fun VehiclePosition.isBetweenTrips(): Boolean = headingDegrees == null && nextStopId == null

/** Parked between trips within the layover radius of [terminus]. */
fun VehiclePosition.isInLayoverAt(terminus: LatLon, rules: PredictionRules = PredictionRules.Default): Boolean =
    isBetweenTrips() && location.metersTo(terminus) <= rules.layoverRadiusMeters
