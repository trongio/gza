package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.config.DefaultGatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import ge.hackerman.gza.core.ttc.config.GatewayConfigPolicy
import ge.hackerman.gza.core.ttc.config.InMemoryTtcConfigCache
import ge.hackerman.gza.core.ttc.firebase.FirebaseEndpoints
import ge.hackerman.gza.core.ttc.firebase.FirebaseRemoteConfigClient
import ge.hackerman.gza.core.ttc.testing.FakeFirebase
import ge.hackerman.gza.core.ttc.testing.FakeRemoteGatewayConfigSource
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.MutableClock
import ge.hackerman.gza.core.ttc.testing.RefreshScope
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TtcHttpLoggingTest {
    private val server = MockWebServer()
    private val lines: MutableList<String> = CopyOnWriteArrayList()
    private val logging = TtcHttpLogging.interceptor { lines += it }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun assertNeverLogged(vararg secrets: String) {
        secrets.forEach { secret -> assertFalse(lines.any { secret in it }, "logged a secret") }
    }

    private fun assertLogged(line: String) {
        assertTrue(line in lines, "missing log line '$line'")
    }

    @Test
    fun `gateway key header is redacted in the log`() {
        server.enqueue(MockResponse.Builder().body("[]").build())
        val remote = FakeRemoteGatewayConfigSource().apply {
            serve(FirebaseFixtures.GATEWAY_KEY, baseUrl = server.url("/pis-gateway").toString())
        }
        val provider = DefaultGatewayConfigProvider(
            cache = InMemoryTtcConfigCache(),
            remote = remote,
            fallback = TtcFallbackConfig("", "", "", "", ""),
            clock = MutableClock(),
            refreshScope = RefreshScope(),
            policy = GatewayConfigPolicy(requireHttpsBaseUrl = false)
        )
        val client = TtcHttpClients.gatewayClient(OkHttpClient(), GatewayAuthInterceptor(provider), logging)

        client.newCall(
            Request.Builder().url("${TtcGateway.PLACEHOLDER_BASE_URL}api/v2/stops").build()
        ).execute().close()

        assertEquals(FirebaseFixtures.GATEWAY_KEY, server.takeRequest().headers[TtcGateway.API_KEY_HEADER])
        assertLogged("${TtcGateway.API_KEY_HEADER}: $REDACTED")
        assertNeverLogged(FirebaseFixtures.GATEWAY_KEY)
        // Logging runs after the rewrite, so it shows the real URL.
        assertTrue(lines.any { it.startsWith("--> GET ${server.url("/pis-gateway/api/v2/stops")}") })
    }

    @Test
    fun `firebase api key and installation tokens are never logged`() {
        val firebase = FakeFirebase()
        server.dispatcher = firebase
        val clock = MutableClock()
        val cache = InMemoryTtcConfigCache()
        // An expiring installation, so the refresh call with the Authorization header runs too.
        runBlocking {
            cache.writeInstallation(
                FirebaseInstallation(
                    FirebaseFixtures.FID,
                    FirebaseFixtures.REFRESH_TOKEN,
                    FirebaseFixtures.AUTH_TOKEN,
                    clock.now + Duration.ofMinutes(10)
                )
            )
        }
        val client = FirebaseRemoteConfigClient(
            httpClient = TtcHttpClients.firebaseClient(OkHttpClient(), logging),
            endpoints = FirebaseEndpoints(server.url("/"), server.url("/")),
            credentials = FirebaseFixtures.credentials,
            cache = cache,
            clock = clock,
            ioDispatcher = Dispatchers.IO
        )

        val config = runBlocking { client.fetch() }

        assertEquals(FirebaseFixtures.GATEWAY_KEY, config.apiKey)
        assertEquals(1, firebase.tokenRefreshes.get())
        assertLogged("x-goog-api-key: $REDACTED")
        assertLogged("Authorization: $REDACTED")
        assertNeverLogged(*FirebaseFixtures.SECRETS.toTypedArray())
    }

    @Test
    fun `key query parameter is redacted`() {
        server.enqueue(MockResponse.Builder().build())
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        client.newCall(Request.Builder().url(server.url("/fetch?key=sentinel-query-key&x=1")).build()).execute().close()
        // OkHttp percent-encodes the redaction marker inside the URL.
        assertTrue(lines.any { it.startsWith("--> GET") && "key=%E2%96%88%E2%96%88&x=1" in it }, lines.toString())
        assertNeverLogged("sentinel-query-key")
    }

    @Test
    fun `every secret header is redacted`() {
        server.enqueue(MockResponse.Builder().build())
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        val request = Request.Builder().url(server.url("/")).apply {
            TtcHttpLogging.REDACTED_HEADERS.forEachIndexed { i, name -> header(name, "sentinel-header-$i") }
        }.build()
        client.newCall(request).execute().close()
        TtcHttpLogging.REDACTED_HEADERS.forEachIndexed { i, name ->
            assertLogged("$name: $REDACTED")
            assertNeverLogged("sentinel-header-$i")
        }
    }

    @Test
    fun `logging never goes beyond headers`() {
        assertEquals(HttpLoggingInterceptor.Level.HEADERS, logging.level)
        server.enqueue(MockResponse.Builder().body("sentinel-response-body").build())
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        val request = Request.Builder().url(server.url("/")).post("sentinel-request-body".toRequestBody()).build()
        client.newCall(request).execute().use { it.body.string() }
        assertTrue(lines.isNotEmpty())
        assertNeverLogged("sentinel-response-body", "sentinel-request-body")
    }

    private companion object {
        const val REDACTED = "██"
    }
}
