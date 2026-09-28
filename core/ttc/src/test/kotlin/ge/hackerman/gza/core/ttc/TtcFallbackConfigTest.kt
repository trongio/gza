package ge.hackerman.gza.core.ttc

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TtcFallbackConfigTest {
    private val config = TtcFallbackConfig(
        gatewayBaseUrl = "https://transit.ttc.com.ge/pis-gateway",
        gatewayKey = "sentinel-key-123",
        firebaseApiKey = "sentinel-firebase-456",
        firebaseProjectId = "tbilisi-transit-production",
        firebaseAppId = "sentinel-app-789"
    )

    @Test
    fun `toString never contains the secrets`() {
        val text = config.toString()
        assertFalse("sentinel-key-123" in text)
        assertFalse("sentinel-firebase-456" in text)
        assertFalse("sentinel-app-789" in text)
        assertContains(text, "gatewayKey=<redacted>")
        assertContains(text, "firebaseApiKey=<redacted>")
        assertContains(text, "firebaseAppId=<redacted>")
    }

    @Test
    fun `toString shows the public fields`() {
        val text = config.toString()
        assertContains(text, "https://transit.ttc.com.ge/pis-gateway")
        assertContains(text, "tbilisi-transit-production")
    }

    @Test
    fun `blank secrets show as empty and there is no gateway key`() {
        val empty = config.copy(gatewayKey = " ", firebaseApiKey = "", firebaseAppId = "")
        assertFalse(empty.hasGatewayKey)
        assertContains(empty.toString(), "gatewayKey=<empty>")
        assertContains(empty.toString(), "firebaseApiKey=<empty>")
        assertContains(empty.toString(), "firebaseAppId=<empty>")
    }

    @Test
    fun `non blank key counts as present`() {
        assertTrue(config.hasGatewayKey)
    }

    @Test
    fun `firebase credentials map the build time fields`() {
        val credentials = config.copy(firebaseProjectId = " tbilisi-transit-production ").firebaseCredentials()
        assertEquals("tbilisi-transit-production", credentials.projectId)
        assertEquals("sentinel-firebase-456", credentials.apiKey)
        assertEquals("sentinel-app-789", credentials.appId)
        assertTrue(credentials.isComplete)
    }

    @Test
    fun `blank firebase fields make the credentials incomplete`() {
        assertFalse(config.copy(firebaseAppId = "").firebaseCredentials().isComplete)
    }
}
