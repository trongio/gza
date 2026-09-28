package ge.hackerman.gza.core.ttc.http

import kotlin.test.assertEquals
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

class GatewayUrlsTest {
    private val base = "https://transit.ttc.com.ge/pis-gateway".toHttpUrl()

    private fun resolve(path: String, base: String = "https://transit.ttc.com.ge/pis-gateway") =
        GatewayUrls.resolve("${TtcGateway.PLACEHOLDER_BASE_URL}$path".toHttpUrl(), base.toHttpUrl()).toString()

    @Test
    fun `stop request lands on the gateway`() {
        assertEquals(
            "https://transit.ttc.com.ge/pis-gateway/api/v2/stops/1:970?locale=en",
            resolve("api/v2/stops/1:970?locale=en")
        )
    }

    @Test
    fun `base with trailing slash gives the same url`() {
        assertEquals(
            resolve("api/v2/stops/1:970?locale=en"),
            resolve("api/v2/stops/1:970?locale=en", "https://transit.ttc.com.ge/pis-gateway/")
        )
    }

    @Test
    fun `encoded segments are preserved byte for byte`() {
        assertEquals(
            "https://transit.ttc.com.ge/pis-gateway/api/v2/stops/1%3A970/a%20b",
            resolve("api/v2/stops/1%3A970/a%20b")
        )
    }

    @Test
    fun `query with repeated params and commas is preserved`() {
        val query = "patternSuffixes=0:01,1:01&locale=en&locale=ka"
        assertEquals(
            "https://transit.ttc.com.ge/pis-gateway/api/v3/routes/1:R97493/positions?$query",
            resolve("api/v3/routes/1:R97493/positions?$query")
        )
    }

    @Test
    fun `port of the base is kept`() {
        assertEquals(
            "http://127.0.0.1:1234/pis-gateway/api/v2/stops",
            resolve("api/v2/stops", "http://127.0.0.1:1234/pis-gateway")
        )
    }

    @Test
    fun `base at root gives the bare path`() {
        assertEquals("http://127.0.0.1:1234/api/v2/stops", resolve("api/v2/stops", "http://127.0.0.1:1234/"))
    }

    @Test
    fun `no query stays without query`() {
        val url = GatewayUrls.resolve("${TtcGateway.PLACEHOLDER_BASE_URL}api/v2/stops".toHttpUrl(), base)
        assertEquals(null, url.encodedQuery)
    }
}
