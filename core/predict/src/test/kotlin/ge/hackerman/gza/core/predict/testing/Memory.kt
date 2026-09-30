package ge.hackerman.gza.core.predict.testing

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.predict.LayoverMemory
import ge.hackerman.gza.core.predict.ParkedKey
import ge.hackerman.gza.core.predict.ParkedVehicle

/** A memory of [entries] (vehicle id to entry), all on [routeId]. */
fun memoryOf(routeId: RouteId, vararg entries: Pair<String, ParkedVehicle>): LayoverMemory =
    LayoverMemory(entries.associate { (vehicle, parked) -> ParkedKey(routeId, VehicleId(vehicle)) to parked })

/** The single entry of [vehicle], on whichever route. */
fun LayoverMemory.of(vehicle: String): ParkedVehicle =
    vehicles.entries.single { it.key.vehicleId.value == vehicle }.value

/** Whether any route remembers [vehicle]. */
fun LayoverMemory.remembers(vehicle: VehicleId): Boolean = vehicles.keys.any { it.vehicleId == vehicle }

val LayoverMemory.vehicleIds: Set<VehicleId> get() = vehicles.keys.map { it.vehicleId }.toSet()
