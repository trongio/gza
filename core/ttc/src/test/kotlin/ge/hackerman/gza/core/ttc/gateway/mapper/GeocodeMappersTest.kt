package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.BoundingBox
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.ttc.gateway.dto.FeatureCollectionDto
import ge.hackerman.gza.core.ttc.gateway.dto.FeatureDto
import ge.hackerman.gza.core.ttc.gateway.dto.GeocodePropertiesDto
import ge.hackerman.gza.core.ttc.gateway.dto.GeometryDto
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class GeocodeMappersTest {
    private val feature = FeatureDto(
        type = "Feature",
        geometry = GeometryDto("Point", listOf(44.7032188, 41.7219641)),
        properties = GeocodePropertiesDto(name = "Politkovskaia Street #16", osmId = 769174152)
    )

    @Test
    fun `coordinates are longitude first`() {
        assertEquals(LatLon(41.7219641, 44.7032188), feature.toGeocodeResultOrNull()?.location)
    }

    @Test
    fun `photon extent is normalised`() {
        val withExtent = feature.copy(
            properties = GeocodePropertiesDto(extent = listOf(44.70, 41.73, 44.71, 41.72))
        )
        assertEquals(BoundingBox(44.70, 41.72, 44.71, 41.73), withExtent.toGeocodeResultOrNull()?.extent)
    }

    @Test
    fun `a short or null extent is null`() {
        val short = feature.copy(properties = GeocodePropertiesDto(extent = listOf(44.7, 41.7, 44.8)))
        val holey = feature.copy(properties = GeocodePropertiesDto(extent = listOf(44.7, null, 44.8, 41.7)))
        assertNull(short.toGeocodeResultOrNull()?.extent)
        assertNull(holey.toGeocodeResultOrNull()?.extent)
    }

    @Test
    fun `non point and one coordinate features are dropped`() {
        assertNull(feature.copy(geometry = GeometryDto("LineString", listOf(44.7, 41.7))).toGeocodeResultOrNull())
        assertNull(feature.copy(geometry = GeometryDto("Point", listOf(44.7))).toGeocodeResultOrNull())
        assertNull(feature.copy(geometry = null).toGeocodeResultOrNull())
    }

    @Test
    fun `a geometry without a type is taken as a point`() {
        assertEquals(
            LatLon(41.7, 44.7),
            feature.copy(geometry = GeometryDto(null, listOf(44.7, 41.7))).toGeocodeResultOrNull()?.location
        )
    }

    @Test
    fun `no features is no results`() {
        assertEquals(emptyList(), FeatureCollectionDto("FeatureCollection", emptyList()).toGeocodeResults())
        assertEquals(emptyList(), FeatureCollectionDto().toGeocodeResults())
        assertEquals(1, FeatureCollectionDto(features = listOf(feature, null)).toGeocodeResults().size)
    }
}
