package ge.hackerman.gza.core.ttc.config

/** The single source of the gateway key and base URL for every gateway request. */
interface GatewayConfigProvider {
    /** Fresh active config or null. Never blocks, never does I/O: the interceptor's fast path. */
    fun peek(): GatewayConfig?

    /**
     * Cache, then Remote Config, then stale cache, then the build-time fallback. When a
     * config exists but is due for a refresh, returns it without waiting and refreshes in
     * the background. Throws [GatewayConfigUnavailableException] when none of them has a usable key.
     */
    suspend fun current(): GatewayConfig

    /**
     * Called after [rejected] got a 401 or 403. Returns a config with different credentials
     * to retry with, or null when a retry would not help.
     */
    suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig?
}
