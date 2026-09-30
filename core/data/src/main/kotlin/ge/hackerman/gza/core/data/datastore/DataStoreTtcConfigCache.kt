package ge.hackerman.gza.core.data.datastore

import androidx.datastore.core.DataStore
import ge.hackerman.gza.core.ttc.config.CachedGatewayConfig
import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The gateway config and Firebase installation in `ttc_config.json`, so a restart reuses both.
 * Not encrypted: the key is served to every visitor of TTC's web page and the installation
 * token only reads that same public config. The file is app private and never backed up.
 * Failures propagate; the config provider and the Firebase client treat them as best effort.
 */
internal class DataStoreTtcConfigCache @Inject constructor(private val dataStore: DataStore<TtcConfigData>) :
    TtcConfigCache {
    override suspend fun readConfig(): CachedGatewayConfig? = dataStore.data.first().config?.let {
        CachedGatewayConfig(it.baseUrl, it.apiKey, Instant.ofEpochMilli(it.fetchedAtMillis))
    }

    override suspend fun writeConfig(config: CachedGatewayConfig) {
        val record = ConfigRecord(config.baseUrl, config.apiKey, config.fetchedAt.toEpochMilli())
        dataStore.updateData { it.copy(config = record) }
    }

    override suspend fun readInstallation(): FirebaseInstallation? = dataStore.data.first().installation?.let {
        FirebaseInstallation(it.fid, it.refreshToken, it.authToken, Instant.ofEpochMilli(it.authTokenExpiresAtMillis))
    }

    override suspend fun writeInstallation(installation: FirebaseInstallation?) {
        val record = installation?.let {
            InstallationRecord(it.fid, it.refreshToken, it.authToken, it.authTokenExpiresAt.toEpochMilli())
        }
        dataStore.updateData { it.copy(installation = record) }
    }
}
