package ge.hackerman.gza.core.ttc.testing

import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfig
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfigSource
import java.util.concurrent.atomic.AtomicInteger

/** Scriptable Remote Config: serves a key, fails, or runs a custom suspending block. */
class FakeRemoteGatewayConfigSource : RemoteGatewayConfigSource {
    val fetches = AtomicInteger()

    @Volatile var behavior: suspend () -> RemoteGatewayConfig = {
        RemoteGatewayConfig(FirebaseFixtures.PRODUCTION_BASE_URL, FirebaseFixtures.GATEWAY_KEY)
    }

    fun serve(key: String, baseUrl: String? = FirebaseFixtures.PRODUCTION_BASE_URL) {
        behavior = { RemoteGatewayConfig(baseUrl, key) }
    }

    fun fail(reason: RemoteConfigException.Reason = RemoteConfigException.Reason.NETWORK) {
        behavior = { throw RemoteConfigException(reason) }
    }

    override suspend fun fetch(): RemoteGatewayConfig {
        fetches.incrementAndGet()
        return behavior()
    }
}
