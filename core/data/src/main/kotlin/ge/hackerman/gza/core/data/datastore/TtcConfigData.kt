package ge.hackerman.gza.core.data.datastore

import kotlinx.serialization.Serializable

/** What `ttc_config.json` holds. Either part can be missing; they are written independently. */
@Serializable
internal data class TtcConfigData(val config: ConfigRecord? = null, val installation: InstallationRecord? = null)

@Serializable
internal data class ConfigRecord(val baseUrl: String, val apiKey: String, val fetchedAtMillis: Long) {
    override fun toString(): String =
        "ConfigRecord(baseUrl=$baseUrl, apiKey=<redacted>, fetchedAtMillis=$fetchedAtMillis)"
}

@Serializable
internal data class InstallationRecord(
    val fid: String,
    val refreshToken: String,
    val authToken: String,
    val authTokenExpiresAtMillis: Long
) {
    override fun toString(): String = "InstallationRecord(fid=$fid, refreshToken=<redacted>, authToken=<redacted>, " +
        "authTokenExpiresAtMillis=$authTokenExpiresAtMillis)"
}
