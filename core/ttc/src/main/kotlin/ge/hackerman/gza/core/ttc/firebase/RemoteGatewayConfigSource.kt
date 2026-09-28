package ge.hackerman.gza.core.ttc.firebase

/** Where a fresh gateway config comes from at runtime. */
fun interface RemoteGatewayConfigSource {
    /** Throws [RemoteConfigException] when no usable key can be fetched. */
    suspend fun fetch(): RemoteGatewayConfig
}

/** [baseUrl] is raw and unvalidated; the provider decides whether to trust it. */
class RemoteGatewayConfig(val baseUrl: String?, val apiKey: String) {
    override fun toString(): String = "RemoteGatewayConfig(baseUrl=$baseUrl, apiKey=<redacted>)"
}
