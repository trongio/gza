package ge.hackerman.gza.core.ttc.http

import okhttp3.HttpUrl

object GatewayUrls {
    /**
     * Moves a request built on [TtcGateway.PLACEHOLDER_BASE_URL] onto [baseUrl], keeping
     * path segments and query exactly as encoded (`1:970`, `%20`, `0:01,1:01`).
     * `.../pis-gateway` and `.../pis-gateway/` give the same result.
     */
    fun resolve(placeholderUrl: HttpUrl, baseUrl: HttpUrl): HttpUrl {
        val baseSegments = baseUrl.encodedPathSegments.filter { it.isNotEmpty() }
        val builder = baseUrl.newBuilder().encodedPath("/")
        // encodedPath("/") leaves a single empty segment that the first add replaces.
        (baseSegments + placeholderUrl.encodedPathSegments).forEach { builder.addEncodedPathSegment(it) }
        return builder.encodedQuery(placeholderUrl.encodedQuery).build()
    }
}
