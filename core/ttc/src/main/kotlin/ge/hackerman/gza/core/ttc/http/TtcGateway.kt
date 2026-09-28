package ge.hackerman.gza.core.ttc.http

/** Fixed names and URLs shared by the gateway client, its interceptor and T03's Retrofit. */
object TtcGateway {
    /**
     * Retrofit needs a base URL at construction, but the real one arrives at runtime from
     * Remote Config. Requests are built on this reserved `.invalid` host (RFC 2606) and
     * [GatewayAuthInterceptor] rewrites them onto the active base URL, so an unrewritten
     * request fails loudly instead of reaching a wrong server.
     */
    const val PLACEHOLDER_HOST: String = "ttc-gateway.invalid"
    const val PLACEHOLDER_BASE_URL: String = "https://$PLACEHOLDER_HOST/"

    /** Same default as the build plugin uses when `ttc.properties` has no base URL. */
    const val DEFAULT_BASE_URL: String = "https://transit.ttc.com.ge/pis-gateway"

    const val API_KEY_HEADER: String = "x-api-key"
}
