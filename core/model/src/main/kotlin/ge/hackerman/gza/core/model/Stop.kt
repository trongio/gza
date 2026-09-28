package ge.hackerman.gza.core.model

/** A stop. [code] is the number printed on the sign; metro and cable car stops have none. */
data class Stop(val id: StopId, val code: String?, val name: String, val location: LatLon, val kind: TransportKind)
