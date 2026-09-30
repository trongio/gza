package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FeatureCollectionDto(val type: String? = null, val features: List<FeatureDto?>? = null)

@Serializable
internal data class FeatureDto(
    val type: String? = null,
    val geometry: GeometryDto? = null,
    val properties: GeocodePropertiesDto? = null
)

/** GeoJSON: `coordinates` is `[lon, lat]`, longitude first. */
@Serializable
internal data class GeometryDto(val type: String? = null, val coordinates: List<Double?>? = null)

/** Photon-style properties; all optional. `extent` is `[minLon, maxLat, maxLon, minLat]`. */
@Serializable
internal data class GeocodePropertiesDto(
    val name: String? = null,
    val street: String? = null,
    @SerialName("housenumber") val houseNumber: String? = null,
    val locality: String? = null,
    val district: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    @SerialName("countrycode") val countryCode: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_key") val osmKey: String? = null,
    @SerialName("osm_value") val osmValue: String? = null,
    val type: String? = null,
    val extent: List<Double?>? = null
)
