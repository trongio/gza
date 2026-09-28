package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.config.CachedGatewayConfig
import ge.hackerman.gza.core.ttc.config.DefaultGatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.GatewayConfigPolicy
import ge.hackerman.gza.core.ttc.config.GatewayConfigUnavailableException
import ge.hackerman.gza.core.ttc.config.InMemoryTtcConfigCache
import ge.hackerman.gza.core.ttc.firebase.FirebaseEndpoints
import ge.hackerman.gza.core.ttc.firebase.FirebaseRemoteConfigClient
import ge.hackerman.gza.core.ttc.testing.FakeFirebase
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.MutableClock
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The backlog acceptance tests: real Firebase client, real provider and an in-memory
 * cache against two MockWebServers, one playing Firebase and one the gateway.
 */
class GatewayAuthInterceptorTest {
    private val firebaseServer = MockWebServer()
    private val gatewayServer = MockWebServer()
    private val firebase = FakeFirebase()
    private val gateway = FakeGateway()
    private val cache = InMemoryTtcConfigCache()
    private val clock = MutableClock()
    private var fallbackKey = FALLBACK_KEY

    private val gatewayBase get() = gatewayServer.url("/pis-gateway").toString()

    @BeforeEach
    fun setUp() {
        firebaseServer.dispatcher = firebase
        gatewayServer.dispatcher = gateway
        firebaseServer.start()
        gatewayServer.start()
        firebase.serveKey(REMOTE_KEY, baseUrl = gatewayBase)
        gateway.validKeys += listOf(REMOTE_KEY, NEW_KEY, FALLBACK_KEY, STALE_KEY)
    }

    @AfterEach
    fun tearDown() {
        firebaseServer.close()
        gatewayServer.close()
    }

    private fun provider() = DefaultGatewayConfigProvider(
        cache = cache,
        remote = FirebaseRemoteConfigClient(
            httpClient = OkHttpClient(),
            endpoints = FirebaseEndpoints(firebaseServer.url("/"), firebaseServer.url("/")),
            credentials = FirebaseFixtures.credentials,
            cache = cache,
            clock = clock,
            ioDispatcher = Dispatchers.IO
        ),
        fallback = TtcFallbackConfig(gatewayBase, fallbackKey, "sentinel-firebase-key", "test-project", "1:0:web:x"),
        clock = clock,
        policy = GatewayConfigPolicy(requireHttpsBaseUrl = false)
    )

    private fun client(provider: DefaultGatewayConfigProvider = provider()) = OkHttpClient.Builder()
        .addInterceptor(GatewayAuthInterceptor(provider))
        .build()

    private fun OkHttpClient.call(url: String = STOP_URL): Int =
        newCall(Request.Builder().url(url).build()).execute().use { it.code }

    private fun seedCache(key: String, age: Duration = Duration.ZERO) = runBlocking {
        cache.writeConfig(CachedGatewayConfig(gatewayBase, key, clock.now - age))
    }

    private fun gatewayKeys(): List<String?> = gateway.requests.map { it.headers[TtcGateway.API_KEY_HEADER] }

    // First launch and reuse

    @Test
    fun `first launch fetches remote config then calls the gateway with the remote key`() {
        assertEquals(200, client().call())
        assertEquals(1, firebase.installations.get())
        assertEquals(1, firebase.fetches.get())
        val request = gateway.requests.single()
        assertEquals("/pis-gateway/api/v2/stops/1:970", request.url.encodedPath)
        assertEquals("en", request.url.queryParameter("locale"))
        assertEquals(REMOTE_KEY, request.headers[TtcGateway.API_KEY_HEADER])
        assertEquals(REMOTE_KEY, runBlocking { cache.readConfig() }?.apiKey)
    }

    @Test
    fun `second request reuses the cached key without firebase calls`() {
        val client = client()
        client.call()
        client.call()
        assertEquals(1, firebase.fetches.get())
        assertEquals(1, firebase.installations.get())
        assertEquals(listOf(REMOTE_KEY, REMOTE_KEY), gatewayKeys())
    }

    @Test
    fun `new provider over a warm cache skips firebase`() {
        client().call()
        val requestsBefore = firebaseServer.requestCount
        assertEquals(200, client(provider()).call())
        assertEquals(requestsBefore, firebaseServer.requestCount)
        assertEquals(1, firebase.fetches.get())
    }

    @Test
    fun `cache older than the ttl is refetched`() {
        val client = client()
        client.call()
        clock.advanceBy(Duration.ofHours(12))
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        assertEquals(200, client.call())
        assertEquals(2, firebase.fetches.get())
        assertEquals(listOf(REMOTE_KEY, NEW_KEY), gatewayKeys())
    }

    // Rotation

    @Test
    fun `401 refetches the key once and retries with the new key`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        assertEquals(200, client().call())
        assertEquals(listOf(OLD_KEY, NEW_KEY), gatewayKeys())
        assertEquals(1, firebase.fetches.get())
        assertEquals(NEW_KEY, runBlocking { cache.readConfig() }?.apiKey)
    }

    @Test
    fun `403 is handled like 401`() {
        seedCache(OLD_KEY)
        gateway.rejectionCode = 403
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        assertEquals(200, client().call())
        assertEquals(listOf(OLD_KEY, NEW_KEY), gatewayKeys())
        assertEquals(1, firebase.fetches.get())
    }

    @Test
    fun `retry that also gets 401 is returned to the caller`() {
        seedCache(OLD_KEY)
        gateway.validKeys.clear()
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        assertEquals(401, client().call())
        assertEquals(listOf(OLD_KEY, NEW_KEY), gatewayKeys())
    }

    @Test
    fun `same key after refetch means no retry`() {
        seedCache(OLD_KEY)
        firebase.serveKey(OLD_KEY, baseUrl = gatewayBase)
        assertEquals(401, client().call())
        assertEquals(listOf(OLD_KEY), gatewayKeys())
        assertEquals(1, firebase.fetches.get())
    }

    @Test
    fun `a second 401 within the cooldown does not refetch`() {
        seedCache(OLD_KEY)
        firebase.serveKey(OLD_KEY, baseUrl = gatewayBase)
        val client = client()
        assertEquals(401, client.call())
        clock.advanceBy(Duration.ofSeconds(30))
        assertEquals(401, client.call())
        assertEquals(1, firebase.fetches.get())
        assertEquals(listOf(OLD_KEY, OLD_KEY), gatewayKeys())
    }

    @Test
    fun `parallel 401s trigger a single refetch and every request retries once`() {
        val parallel = 10
        seedCache(OLD_KEY)
        val allArrived = CountDownLatch(parallel)
        gateway.beforeRejecting = {
            allArrived.countDown()
            allArrived.await(5, TimeUnit.SECONDS)
        }
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase, headersDelayMs = 300)
        val client = client()
        val pool = Executors.newFixedThreadPool(parallel)
        try {
            val codes = List(parallel) { pool.submit<Int> { client.call() } }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(List(parallel) { 200 }, codes)
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, firebase.fetches.get())
        assertEquals(parallel, gatewayKeys().count { it == OLD_KEY })
        assertEquals(parallel, gatewayKeys().count { it == NEW_KEY })
    }

    @Test
    fun `400, 404 and 500 are returned without refetch`() {
        val client = client()
        listOf(400, 404, 500).forEach { code ->
            gateway.fixedCode = code
            assertEquals(code, client.call())
        }
        assertEquals(1, firebase.fetches.get())
        assertEquals(3, gateway.requests.size)
    }

    // Remote Config down

    @Test
    fun `remote config down uses the build time fallback key`() {
        firebase.failFetch(503)
        assertEquals(200, client().call())
        assertEquals(listOf(FALLBACK_KEY), gatewayKeys())
        assertNull(runBlocking { cache.readConfig() })
    }

    @Test
    fun `remote config unreachable uses the build time fallback key`() {
        val client = client()
        firebaseServer.close()
        assertEquals(200, client.call())
        assertEquals(listOf(FALLBACK_KEY), gatewayKeys())
        assertNull(runBlocking { cache.readConfig() })
    }

    @Test
    fun `stale cache preferred over fallback when remote is down`() {
        seedCache(STALE_KEY, age = Duration.ofDays(3))
        firebase.failFetch(503)
        assertEquals(200, client().call())
        assertEquals(listOf(STALE_KEY), gatewayKeys())
    }

    @Test
    fun `no key anywhere fails the call with an IOException and sends nothing to the gateway`() {
        fallbackKey = ""
        firebase.failFetch(503)
        assertFailsWith<GatewayConfigUnavailableException> { client().call() }
        assertEquals(0, gatewayServer.requestCount)
    }

    @Test
    fun `requests to other hosts get no key`() {
        val client = client()
        client.call(gatewayServer.url("/pis-gateway/api/v2/stops").toString())
        assertNull(gateway.requests.single().headers[TtcGateway.API_KEY_HEADER])
        assertEquals(0, firebaseServer.requestCount)
    }

    @Test
    fun `rewritten request carries the gateway host, not the placeholder`() {
        client().call()
        val host = gateway.requests.single().headers["Host"].orEmpty()
        assertEquals("${gatewayServer.hostName}:${gatewayServer.port}", host)
        assertTrue(TtcGateway.PLACEHOLDER_HOST !in host)
    }

    /** Answers 200 to [validKeys], [rejectionCode] to any other key, or [fixedCode] when set. */
    private class FakeGateway : Dispatcher() {
        val validKeys: MutableList<String> = CopyOnWriteArrayList()
        val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

        @Volatile var rejectionCode = 401

        @Volatile var fixedCode: Int? = null

        @Volatile var beforeRejecting: () -> Unit = {}

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            val code = fixedCode ?: if (request.headers[TtcGateway.API_KEY_HEADER] in validKeys) {
                200
            } else {
                beforeRejecting()
                rejectionCode
            }
            return MockResponse.Builder().code(code).body(if (code == 200) "[]" else "Unauthorized").build()
        }
    }

    private companion object {
        const val STOP_URL = "https://ttc-gateway.invalid/api/v2/stops/1:970?locale=en"
        const val REMOTE_KEY = "sentinel-gateway-key-0001"
        const val OLD_KEY = "sentinel-old-key"
        const val NEW_KEY = "sentinel-new-key"
        const val STALE_KEY = "sentinel-stale-key"
        const val FALLBACK_KEY = "sentinel-fallback-key"
    }
}
