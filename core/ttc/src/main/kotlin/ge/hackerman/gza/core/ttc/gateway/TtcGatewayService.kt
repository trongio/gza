package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.gateway.dto.RouteDetailDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * The gateway as Retrofit sees it. Paths have no leading slash so the interceptor can put
 * them under the configured base (`.../pis-gateway`).
 *
 * List and map responses, and objects holding a list, come back as [JsonElement] and are
 * decoded by the client element by element (see `LenientDecoding.kt`): decoded here in one
 * go, one mistyped element would fail the whole response.
 */
@Suppress("TooManyFunctions") // Mirrors the gateway, one function per endpoint.
internal interface TtcGatewayService {
    @GET("api/v2/stops")
    suspend fun stops(@Query("locale") locale: String): JsonElement

    @GET("api/v2/stops/{stopId}")
    suspend fun stop(@Path("stopId") stopId: String, @Query("locale") locale: String): StopDto

    @GET("api/v2/stops/{stopId}/routes")
    suspend fun stopRoutes(@Path("stopId") stopId: String, @Query("locale") locale: String): JsonElement

    @GET("api/v2/stops/{stopId}/arrival-times")
    suspend fun arrivalTimes(
        @Path("stopId") stopId: String,
        @Query("locale") locale: String,
        @Query("ignoreScheduledArrivalTimes") ignoreScheduledArrivalTimes: Boolean = false
    ): JsonElement

    @GET("api/v3/routes")
    suspend fun routes(@Query("modes") modes: String, @Query("locale") locale: String): JsonElement

    @GET("api/v3/routes/{routeId}")
    suspend fun route(@Path("routeId") routeId: String, @Query("locale") locale: String): RouteDetailDto

    @GET("api/v3/routes/{routeId}/schedule")
    suspend fun schedule(
        @Path("routeId") routeId: String,
        @Query("patternSuffix") patternSuffix: String,
        @Query("locale") locale: String
    ): JsonElement

    /** Takes a comma list, but only a single suffix gives travel order. */
    @GET("api/v3/routes/{routeId}/stops-of-patterns")
    suspend fun stopsOfPatterns(
        @Path("routeId") routeId: String,
        @Query("patternSuffixes") patternSuffixes: String,
        @Query("locale") locale: String
    ): JsonElement

    @GET("api/v3/routes/{routeId}/polylines")
    suspend fun polylines(
        @Path("routeId") routeId: String,
        @Query("patternSuffixes") patternSuffixes: String
    ): JsonElement

    @GET("api/v3/routes/{routeId}/positions")
    suspend fun positions(
        @Path("routeId") routeId: String,
        @Query("patternSuffixes") patternSuffixes: String
    ): JsonElement

    /** Eight parameters, some optional: [QueryFormat.planQuery] builds them in the gateway's order. */
    @GET("api/v2/plan")
    suspend fun plan(@QueryMap query: Map<String, String>): JsonElement

    @GET("api/v2/geocode")
    suspend fun geocode(
        @Query("query") query: String,
        @Query("locale") locale: String,
        @Query("bbox") bbox: String
    ): JsonElement

    @GET("api/v2/geocode/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("locale") locale: String
    ): JsonElement
}
