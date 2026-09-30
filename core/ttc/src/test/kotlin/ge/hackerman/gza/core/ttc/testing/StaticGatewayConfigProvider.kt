package ge.hackerman.gza.core.ttc.testing

import ge.hackerman.gza.core.ttc.config.ConfigSource
import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import java.io.IOException
import okhttp3.HttpUrl

/**
 * A provider with a fixed key, for client tests that are not about key loading. Offers
 * [rotatedKey] once after a rejection; with [failure] set it has no key at all.
 */
class StaticGatewayConfigProvider(
    private val baseUrl: HttpUrl,
    key: String,
    private val rotatedKey: String? = null,
    private val failure: IOException? = null
) : GatewayConfigProvider {
    @Volatile private var config = GatewayConfig(baseUrl, key, ConfigSource.REMOTE, null, 1)

    override fun peek(): GatewayConfig? = if (failure == null) config else null

    override suspend fun current(): GatewayConfig = failure?.let { throw it } ?: config

    override suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig? =
        rotatedKey?.takeIf { it != rejected.apiKey }?.let {
            GatewayConfig(baseUrl, it, ConfigSource.REMOTE, null, rejected.generation + 1).also { new -> config = new }
        }
}
