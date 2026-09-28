package ge.hackerman.gza.core.ttc

import ge.hackerman.gza.core.ttc.config.CachedGatewayConfig
import ge.hackerman.gza.core.ttc.config.ConfigSource
import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigUnavailableException
import ge.hackerman.gza.core.ttc.firebase.FirebaseWebCredentials
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertFalse
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

/** Every object or message that could carry a secret must print it redacted. */
class RedactionTest {
    private val secrets = listOf(GATEWAY_KEY, FIREBASE_KEY, APP_ID, REFRESH_TOKEN, AUTH_TOKEN)

    private fun assertNoSecrets(text: String?) {
        val value = text.orEmpty()
        secrets.forEach { assertFalse(it in value, "leaked a secret in: $value") }
    }

    @Test
    fun `gateway config hides the key`() {
        val config = GatewayConfig(
            baseUrl = "https://transit.ttc.com.ge/pis-gateway".toHttpUrl(),
            apiKey = GATEWAY_KEY,
            source = ConfigSource.REMOTE,
            fetchedAt = Instant.EPOCH,
            generation = 1
        )
        assertNoSecrets(config.toString())
        assertContains(config.toString(), "apiKey=<redacted>")
        assertContains(config.toString(), "transit.ttc.com.ge")
    }

    @Test
    fun `firebase credentials hide the api key and app id`() {
        val text = FirebaseWebCredentials(FIREBASE_KEY, "test-project", APP_ID).toString()
        assertNoSecrets(text)
        assertContains(text, "apiKey=<redacted>")
        assertContains(text, "appId=<redacted>")
        assertContains(text, "test-project")
    }

    @Test
    fun `cached config hides the key`() {
        val text = CachedGatewayConfig("https://transit.ttc.com.ge/pis-gateway", GATEWAY_KEY, Instant.EPOCH).toString()
        assertNoSecrets(text)
        assertContains(text, "apiKey=<redacted>")
    }

    @Test
    fun `firebase installation hides both tokens`() {
        val text = FirebaseInstallation("fid-public", REFRESH_TOKEN, AUTH_TOKEN, Instant.EPOCH).toString()
        assertNoSecrets(text)
        assertContains(text, "refreshToken=<redacted>")
        assertContains(text, "authToken=<redacted>")
        assertContains(text, "fid-public")
    }

    @Test
    fun `unavailable exception message carries no secret`() {
        assertNoSecrets(GatewayConfigUnavailableException("No gateway key").message)
    }

    companion object {
        const val GATEWAY_KEY = "sentinel-gateway-key-0001"
        const val FIREBASE_KEY = "sentinel-firebase-key"
        const val APP_ID = "1:0:web:sentinel"
        const val REFRESH_TOKEN = "sentinel-refresh-token-0001"
        const val AUTH_TOKEN = "sentinel-auth-token-0001"
    }
}
