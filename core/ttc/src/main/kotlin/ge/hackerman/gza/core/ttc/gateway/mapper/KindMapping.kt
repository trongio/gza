package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.TransportKind
import java.util.Locale

internal object KindMapping {
    /**
     * The gateway's `mode`/`vehicleMode` to a kind. Minibuses say `BUS` too, so only a
     * minibus [routeId] makes [TransportKind.MINIBUS]; stops and board rows pass null.
     */
    fun fromMode(mode: String?, routeId: RouteId?): TransportKind = when (mode?.trim()?.uppercase(Locale.ROOT)) {
        "BUS" -> if (routeId?.isMinibus == true) TransportKind.MINIBUS else TransportKind.BUS
        "SUBWAY" -> TransportKind.METRO
        "GONDOLA" -> TransportKind.CABLE_CAR
        else -> TransportKind.UNKNOWN
    }
}
