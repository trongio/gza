package ge.hackerman.gza.core.ttc.config

/**
 * Process-lifetime cache. Writes are already serialized by the provider mutex and the
 * Firebase client, and reading one reference is atomic, so volatile fields are enough.
 */
class InMemoryTtcConfigCache : TtcConfigCache {
    @Volatile private var config: CachedGatewayConfig? = null

    @Volatile private var installation: FirebaseInstallation? = null

    override suspend fun readConfig(): CachedGatewayConfig? = config

    override suspend fun writeConfig(config: CachedGatewayConfig) {
        this.config = config
    }

    override suspend fun readInstallation(): FirebaseInstallation? = installation

    override suspend fun writeInstallation(installation: FirebaseInstallation?) {
        this.installation = installation
    }
}
