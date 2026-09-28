package ge.hackerman.gza.core.ttc

import ge.hackerman.gza.core.ttc.firebase.FirebaseWebCredentials

/**
 * Gateway settings baked in at build time from `ttc.properties`. Used only when the key
 * cannot be fetched from TTC's Remote Config at runtime.
 */
data class TtcFallbackConfig(
    val gatewayBaseUrl: String,
    val gatewayKey: String,
    val firebaseApiKey: String,
    val firebaseProjectId: String,
    val firebaseAppId: String
) {
    val hasGatewayKey: Boolean get() = gatewayKey.isNotBlank()

    fun firebaseCredentials(): FirebaseWebCredentials = FirebaseWebCredentials(
        apiKey = firebaseApiKey.trim(),
        projectId = firebaseProjectId.trim(),
        appId = firebaseAppId.trim()
    )

    // A data class prints every field, so a logged config would leak the key.
    override fun toString(): String = "TtcFallbackConfig(" +
        "gatewayBaseUrl=$gatewayBaseUrl, " +
        "gatewayKey=${redact(gatewayKey)}, " +
        "firebaseApiKey=${redact(firebaseApiKey)}, " +
        "firebaseProjectId=$firebaseProjectId, " +
        "firebaseAppId=${redact(firebaseAppId)})"

    private fun redact(secret: String): String = if (secret.isBlank()) "<empty>" else "<redacted>"
}
