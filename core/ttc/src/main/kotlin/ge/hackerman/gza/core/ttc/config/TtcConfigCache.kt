package ge.hackerman.gza.core.ttc.config

import java.time.Instant

/**
 * Persistent store for the gateway config and the Firebase installation. Plain strings and
 * [Instant]s so a DataStore implementation (T04) can store them as they are. Keeping the
 * installation here too means a restart reuses it instead of registering a new one.
 */
interface TtcConfigCache {
    suspend fun readConfig(): CachedGatewayConfig?
    suspend fun writeConfig(config: CachedGatewayConfig)
    suspend fun readInstallation(): FirebaseInstallation?

    /** Null clears the stored installation. */
    suspend fun writeInstallation(installation: FirebaseInstallation?)
}

data class CachedGatewayConfig(val baseUrl: String, val apiKey: String, val fetchedAt: Instant) {
    override fun toString(): String = "CachedGatewayConfig(baseUrl=$baseUrl, apiKey=<redacted>, fetchedAt=$fetchedAt)"
}

data class FirebaseInstallation(
    val fid: String,
    val refreshToken: String,
    val authToken: String,
    val authTokenExpiresAt: Instant
) {
    override fun toString(): String = "FirebaseInstallation(fid=$fid, refreshToken=<redacted>, " +
        "authToken=<redacted>, authTokenExpiresAt=$authTokenExpiresAt)"
}
