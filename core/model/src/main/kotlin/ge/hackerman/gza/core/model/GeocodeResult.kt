package ge.hackerman.gza.core.model

/** One place found by (reverse) geocoding. Everything but the location is optional. */
data class GeocodeResult(
    val name: String?,
    val location: LatLon,
    val street: String?,
    val houseNumber: String?,
    val locality: String?,
    val district: String?,
    val city: String?,
    val postcode: String?,
    val osmId: Long?,
    val osmKey: String?,
    val osmValue: String?,
    val type: String?,
    val extent: BoundingBox?
)
