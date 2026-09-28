package ge.hackerman.gza.core.ttc.config

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GatewayKeysTest {
    @Test
    fun `uuid like key is usable`() {
        assertTrue(GatewayKeys.isUsableApiKey("0a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d"))
    }

    @Test
    fun `blank, spaced, control, non ascii and too long keys are unusable`() {
        listOf(null, "", "   ", "abc def", "abc\n", "\tabc", "key-გ", "a".repeat(257)).forEach {
            assertFalse(GatewayKeys.isUsableApiKey(it), "expected unusable: ${it?.length}")
        }
        assertTrue(GatewayKeys.isUsableApiKey("a".repeat(256)))
    }

    @Test
    fun `header safe means non empty visible ascii of any length`() {
        assertTrue(GatewayKeys.isHeaderSafe("a".repeat(2_000)))
        assertTrue(GatewayKeys.isHeaderSafe("3_AS3qfw-LWq.zKF~"))
        listOf(null, "", " ", "a b", "a\u0000", "a\u007f", "\u00e9").forEach {
            assertFalse(GatewayKeys.isHeaderSafe(it), "expected unsafe: ${it?.length}")
        }
    }

    @Test
    fun `https base url is accepted`() {
        val url = assertNotNull(GatewayKeys.parseBaseUrl("https://transit.ttc.com.ge/pis-gateway", true))
        assertEquals("/pis-gateway", url.encodedPath)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertNotNull(GatewayKeys.parseBaseUrl("  https://transit.ttc.com.ge/pis-gateway\n", true))
    }

    @Test
    fun `http is rejected when https is required and accepted otherwise`() {
        assertNull(GatewayKeys.parseBaseUrl("http://transit.ttc.com.ge/pis-gateway", true))
        assertNotNull(GatewayKeys.parseBaseUrl("http://127.0.0.1:1234/pis-gateway", false))
    }

    @Test
    fun `garbage, empty and null are rejected`() {
        assertNull(GatewayKeys.parseBaseUrl("not a url", false))
        assertNull(GatewayKeys.parseBaseUrl("", false))
        assertNull(GatewayKeys.parseBaseUrl(null, false))
        assertNull(GatewayKeys.parseBaseUrl("ftp://example.com/", false))
    }

    @Test
    fun `url with query or fragment is rejected`() {
        assertNull(GatewayKeys.parseBaseUrl("https://transit.ttc.com.ge/pis-gateway?x=1", true))
        assertNull(GatewayKeys.parseBaseUrl("https://transit.ttc.com.ge/pis-gateway#top", true))
    }

    @Test
    fun `trailing slash is kept as given`() {
        val url = assertNotNull(GatewayKeys.parseBaseUrl("https://transit.ttc.com.ge/pis-gateway/", true))
        assertEquals("/pis-gateway/", url.encodedPath)
    }
}
