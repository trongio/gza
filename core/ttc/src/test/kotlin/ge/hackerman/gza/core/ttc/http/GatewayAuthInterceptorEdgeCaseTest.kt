package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.config.CachedGatewayConfig
import ge.hackerman.gza.core.ttc.config.DefaultGatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigPolicy
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.InMemoryTtcConfigCache
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import ge.hackerman.gza.core.ttc.firebase.FirebaseEndpoints
import ge.hackerman.gza.core.ttc.firebase.FirebaseRemoteConfigClient
import ge.hackerman.gza.core.ttc.testing.FakeFirebase
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.MutableClock
import ge.hackerman.gza.core.ttc.testing.RefreshScope
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Edge cases around the acceptance tests in [GatewayAuthInterceptorTest]: loops, leaked
 * connections, redirects, request bodies, cancellation and base URL rotation. Same wiring:
 * real Firebase client, real provider, in-memory cache, MockWebServers for both sides.
 */
class GatewayAuthInterceptorEdgeCaseTest {
    private val firebaseServer = MockWebServer()
    private val gatewayServer = MockWebServer()
    private val otherServer = MockWebServer()
    private val firebase = FakeFirebase()
    private val gateway = RecordingGateway()
    private val other = RecordingGateway()
    private val cache = InMemoryTtcConfigCache()
    private val clock = MutableClock()
    private val refreshScope = RefreshScope()
    private var fallbackKey = FALLBACK_KEY

    private val gatewayBase get() = gatewayServer.url("/pis-gateway").toString()

    @BeforeEach
    fun setUp() {
        firebaseServer.dispatcher = firebase
        gatewayServer.dispatcher = gateway
        otherServer.dispatcher = other
        firebaseServer.start()
        gatewayServer.start()
        otherServer.start()
        firebase.serveKey(REMOTE_KEY, baseUrl = gatewayBase)
        gateway.validKeys += listOf(REMOTE_KEY, NEW_KEY, FALLBACK_KEY)
        other.validKeys += listOf(REMOTE_KEY, NEW_KEY, FALLBACK_KEY)
    }

    @AfterEach
    fun tearDown() {
        refreshScope.close()
        firebaseServer.close()
        gatewayServer.close()
        otherServer.close()
    }

    private fun provider(configCache: TtcConfigCache = cache) = DefaultGatewayConfigProvider(
        cache = configCache,
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
        refreshScope = refreshScope,
        policy = GatewayConfigPolicy(requireHttpsBaseUrl = false)
    )

    // Every request the auth interceptor hands on, including ones OkHttp would then refuse.
    private val proceeded: MutableList<Request> = CopyOnWriteArrayList()

    // The production gateway client, so its redirect policy is part of what is tested. The
    // logging slot, right after auth, records what auth passes on.
    private fun client(provider: GatewayConfigProvider = provider()) = TtcHttpClients.gatewayClient(
        OkHttpClient(),
        GatewayAuthInterceptor(provider),
        logging = { chain -> chain.proceed(chain.request().also { proceeded += it }) }
    )

    private fun OkHttpClient.call(request: Request = get()): Int = newCall(request).execute().use { it.code }

    private fun get(url: String = STOP_URL) = Request.Builder().url(url).build()

    private fun seedCache(key: String, base: String = gatewayBase) = runBlocking {
        cache.writeConfig(CachedGatewayConfig(base, key, clock.now))
    }

    private fun RecordingGateway.keys(): List<String?> = requests.map { it.headers[TtcGateway.API_KEY_HEADER] }

    // No loops

    @Test
    fun `401 on the retry is returned without a third request or a second refetch`() {
        seedCache(OLD_KEY)
        gateway.validKeys.clear()
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        val client = client()

        assertEquals(401, client.call())
        assertEquals(listOf(OLD_KEY, NEW_KEY), gateway.keys())
        assertEquals(1, firebase.fetches.get())

        // The dead new key inside the cooldown: one request, no refetch.
        clock.advanceBy(Duration.ofSeconds(10))
        assertEquals(401, client.call())
        assertEquals(listOf(OLD_KEY, NEW_KEY, NEW_KEY), gateway.keys())
        assertEquals(1, firebase.fetches.get())
    }

    @Test
    fun `a dead key causes at most one refetch per cooldown under a burst of requests`() {
        seedCache(OLD_KEY)
        gateway.validKeys.clear()
        firebase.serveKey(OLD_KEY, baseUrl = gatewayBase)
        val client = client()
        repeat(20) { assertEquals(401, client.call()) }
        assertEquals(1, firebase.fetches.get())
        assertEquals(20, gateway.requests.size)
        clock.advanceBy(Duration.ofSeconds(61))
        assertEquals(401, client.call())
        assertEquals(2, firebase.fetches.get())
    }

    // Response bodies and connections

    @Test
    fun `rejected response is closed before the retry so the connection is reused`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        gateway.rejectionBody = "x".repeat(4_096)
        val client = client()

        assertEquals(200, client.call())

        val (first, retry) = gateway.requests
        assertEquals(OLD_KEY, first.headers[TtcGateway.API_KEY_HEADER])
        assertEquals(NEW_KEY, retry.headers[TtcGateway.API_KEY_HEADER])
        // A 401 left open would pin its connection and force a second one for the retry.
        assertEquals(first.connectionIndex, retry.connectionIndex)
        assertEquals(first.exchangeIndex + 1, retry.exchangeIndex)
        assertEquals(0, client.connectionPool.connectionCount() - client.connectionPool.idleConnectionCount())
    }

    @Test
    fun `rejected response is closed when the refetch throws`() {
        seedCache(OLD_KEY)
        gateway.rejectionBody = "x".repeat(4_096)
        val real = provider()
        val failingRefresh = object : GatewayConfigProvider by real {
            override suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig? =
                throw IOException("refresh broke")
        }
        val client = client(failingRefresh)

        val error = assertFailsWith<IOException> { client.call() }

        assertEquals("refresh broke", error.message)
        assertEquals(listOf<String?>(OLD_KEY), gateway.keys())
        // A 401 left open would keep its connection in use.
        assertEquals(0, client.connectionPool.connectionCount() - client.connectionPool.idleConnectionCount())
    }

    @Test
    fun `a cache write that fails during a 401 refetch still retries with the rotated key`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        val failingCache = object : TtcConfigCache by cache {
            override suspend fun writeConfig(config: CachedGatewayConfig) = throw IOException("disk full")
        }
        val client = client(provider(failingCache))

        assertEquals(200, client.call())

        assertEquals(listOf<String?>(OLD_KEY, NEW_KEY), gateway.keys())
        assertEquals(0, client.connectionPool.connectionCount() - client.connectionPool.idleConnectionCount())
    }

    @Test
    fun `the caller can read the body of a rejection that is not retried`() {
        seedCache(OLD_KEY)
        firebase.serveKey(OLD_KEY, baseUrl = gatewayBase)
        client().newCall(get()).execute().use { response ->
            assertEquals(401, response.code)
            assertEquals("Unauthorized", response.body.string())
        }
    }

    // Where the key may go

    @Test
    fun `key is never sent to a non gateway host even on the placeholder path`() {
        val client = client()
        client.call(get(otherServer.url("/api/v2/stops/1:970").toString()))
        client.call(get("${otherServer.url("/")}pis-gateway/api/v2/stops?key=x"))
        assertEquals(listOf<String?>(null, null), other.keys())
        assertEquals(0, firebaseServer.requestCount)
    }

    @Test
    fun `gateway key never reaches firebase`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        client().call()
        assertTrue(firebase.requests.isNotEmpty())
        firebase.requests.forEach { request ->
            assertNull(request.headers[TtcGateway.API_KEY_HEADER])
            assertFalse(OLD_KEY in request.body?.utf8().orEmpty())
            assertFalse(NEW_KEY in request.body?.utf8().orEmpty())
        }
    }

    // The gateway client follows no redirects at all (see TtcHttpClients.gatewayClient), so
    // even a same host 3xx reaches the caller as is and the key is sent exactly once.
    @Test
    fun `same host redirect is returned to the caller without following it`() {
        gateway.redirectTo =
            { if (it.url.encodedPath.endsWith("/old")) gatewayServer.url("/pis-gateway/api/new") else null }
        assertEquals(302, client().call(get("https://ttc-gateway.invalid/api/old")))
        assertEquals(listOf<String?>(REMOTE_KEY), gateway.keys())
    }

    // OkHttp strips only Authorization on a cross host redirect, so a followed redirect
    // would carry x-api-key to the other host.
    @Test
    fun `key is not forwarded when the gateway redirects to another host`() {
        gateway.redirectTo = { otherServer.url("/elsewhere") }
        assertEquals(302, client().call())
        assertTrue(other.requests.isEmpty(), "gateway client followed a cross host redirect")
        assertEquals(0, otherServer.requestCount)
    }

    // Missing or blank keys (the gateway answers 400 when the header is absent)

    @Test
    fun `a blank cached key is never sent and the fallback is used when remote is down`() {
        seedCache("   ")
        firebase.failFetch(503)
        assertEquals(200, client().call())
        assertEquals(listOf<String?>(FALLBACK_KEY), gateway.keys())
    }

    @Test
    fun `400 for a missing key is returned untouched without refetch or retry`() {
        gateway.fixedCode = 400
        assertEquals(400, client().call())
        assertEquals(1, gateway.requests.size)
        assertEquals(1, firebase.fetches.get())
    }

    // Rotation variants

    @Test
    fun `401 with remote down retries once with the fallback key`() {
        seedCache(OLD_KEY)
        firebase.failFetch(503)
        assertEquals(200, client().call())
        assertEquals(listOf(OLD_KEY, FALLBACK_KEY), gateway.keys())
        assertEquals(1, firebase.fetches.get())
        assertEquals(OLD_KEY, runBlocking { cache.readConfig() }?.apiKey)
    }

    @Test
    fun `rotated base url moves the retry to the new gateway host`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = otherServer.url("/v2-gateway").toString())
        assertEquals(200, client().call())
        assertEquals(listOf(OLD_KEY), gateway.keys())
        val retry = other.requests.single()
        assertEquals("/v2-gateway/api/v2/stops/1:970", retry.url.encodedPath)
        assertEquals(NEW_KEY, retry.headers[TtcGateway.API_KEY_HEADER])
    }

    @Test
    fun `fallback is replaced by the remote key once the backoff passes`() {
        firebase.failFetch(503)
        val client = client()
        assertEquals(200, client.call())
        clock.advanceBy(Duration.ofSeconds(30))
        assertEquals(200, client.call())
        assertEquals(1, firebase.fetches.get())
        firebase.serveKey(REMOTE_KEY, baseUrl = gatewayBase)
        clock.advanceBy(Duration.ofSeconds(31))
        // The request that ends the backoff still uses the fallback; the refetch runs behind it.
        assertEquals(200, client.call())
        refreshScope.awaitRefreshes()
        assertEquals(2, firebase.fetches.get())
        assertEquals(200, client.call())
        assertEquals(listOf(FALLBACK_KEY, FALLBACK_KEY, FALLBACK_KEY, REMOTE_KEY), gateway.keys())
    }

    @Test
    fun `expired cache with slow firebase serves parallel requests at once with one fetch`() {
        seedCache(OLD_KEY)
        gateway.validKeys += OLD_KEY
        clock.advanceBy(Duration.ofHours(13))
        val releaseFetch = CountDownLatch(1)
        firebase.onFetch = {
            releaseFetch.await(10, TimeUnit.SECONDS)
            FirebaseFixtures.json(200, FirebaseFixtures.fetchOk(NEW_KEY, gatewayBase))
        }
        val parallel = 10
        val client = client()
        val pool = Executors.newFixedThreadPool(parallel)
        try {
            // Firebase is held until every request is done: a request that waited on the
            // refresh would time out here instead of returning.
            val codes = List(parallel) { pool.submit<Int> { client.call() } }.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(List(parallel) { 200 }, codes)
            assertEquals(List<String?>(parallel) { OLD_KEY }, gateway.keys())
        } finally {
            releaseFetch.countDown()
            pool.shutdownNow()
        }
        refreshScope.awaitRefreshes()
        assertEquals(1, firebase.fetches.get())
        assertEquals(200, client.call())
        assertEquals(NEW_KEY, gateway.keys().last())
        assertEquals(1, firebase.fetches.get())
    }

    @Test
    fun `a 401 during a background refresh waits for it and retries with its key`() {
        seedCache(OLD_KEY)
        clock.advanceBy(Duration.ofHours(13))
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        firebase.onFetch = {
            fetchStarted.countDown()
            releaseFetch.await(10, TimeUnit.SECONDS)
            FirebaseFixtures.json(200, FirebaseFixtures.fetchOk(NEW_KEY, gatewayBase))
        }
        val client = client()
        val pool = Executors.newSingleThreadExecutor()
        try {
            // OLD_KEY is dead: the request goes out with it, gets 401, then must wait for the
            // running refresh rather than retry with a key known to be bad.
            val result = pool.submit<Int> { client.call() }
            assertTrue(fetchStarted.await(5, TimeUnit.SECONDS), "background refresh never started")
            releaseFetch.countDown()
            assertEquals(200, result.get(10, TimeUnit.SECONDS))
        } finally {
            releaseFetch.countDown()
            pool.shutdownNow()
        }
        assertEquals(listOf(OLD_KEY, NEW_KEY), gateway.keys())
        assertEquals(1, firebase.fetches.get())
    }

    @Test
    fun `parallel cold start requests share one remote config fetch`() {
        val parallel = 10
        firebase.serveKey(REMOTE_KEY, baseUrl = gatewayBase, headersDelayMs = 300)
        val client = client()
        val pool = Executors.newFixedThreadPool(parallel)
        try {
            val codes = List(parallel) { pool.submit<Int> { client.call() } }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(List(parallel) { 200 }, codes)
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, firebase.fetches.get())
        assertEquals(1, firebase.installations.get())
        assertEquals(List<String?>(parallel) { REMOTE_KEY }, gateway.keys())
    }

    // Request bodies

    @Test
    fun `post body is replayed unchanged on the retry`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        val body = """{"stops":["1:970"]}""".toRequestBody("application/json".toMediaType())
        assertEquals(200, client().call(Request.Builder().url(STOP_URL).post(body).build()))
        assertEquals(listOf(OLD_KEY, NEW_KEY), gateway.keys())
        gateway.requests.forEach { assertEquals("""{"stops":["1:970"]}""", it.body?.utf8()) }
    }

    @Test
    fun `one shot body is not retried and does not refetch`() {
        seedCache(OLD_KEY)
        firebase.serveKey(NEW_KEY, baseUrl = gatewayBase)
        val oneShot = object : RequestBody() {
            override fun contentType(): MediaType = "text/plain".toMediaType()
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("once")
            }
        }
        assertEquals(401, client().call(Request.Builder().url(STOP_URL).post(oneShot).build()))
        assertEquals(listOf(OLD_KEY), gateway.keys())
        assertEquals(0, firebase.fetches.get())
    }

    // Cancellation

    @Test
    fun `cancel during the refetch fails the call and leaves the provider usable`() {
        seedCache(OLD_KEY)
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        firebase.onFetch = {
            fetchStarted.countDown()
            releaseFetch.await(5, TimeUnit.SECONDS)
            FirebaseFixtures.json(200, FirebaseFixtures.fetchOk(NEW_KEY, gatewayBase))
        }
        val client = client()
        val call = client.newCall(get())
        val outcome = CompletableFuture<Any>()
        call.enqueue(outcome.asCallback())

        assertTrue(fetchStarted.await(5, TimeUnit.SECONDS), "refetch never started")
        call.cancel()
        releaseFetch.countDown()

        assertEquals("Canceled", assertIs<IOException>(outcome.get(10, TimeUnit.SECONDS)).message)
        // The cancelled call never reached the gateway with the new key, nor even tried to.
        assertEquals(listOf<String?>(OLD_KEY), gateway.keys())
        assertEquals(listOf<String?>(OLD_KEY), proceeded.map { it.header(TtcGateway.API_KEY_HEADER) })
        // The mutex was released and the refetched key kept: the next call needs no Firebase.
        assertEquals(200, client.call())
        assertEquals(1, firebase.fetches.get())
        assertEquals(listOf(OLD_KEY, NEW_KEY), gateway.keys())
    }

    @Test
    fun `cancel during the cold start fetch fails the call and a later call succeeds`() {
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        firebase.onFetch = {
            fetchStarted.countDown()
            releaseFetch.await(5, TimeUnit.SECONDS)
            FirebaseFixtures.json(200, FirebaseFixtures.fetchOk(REMOTE_KEY, gatewayBase))
        }
        val client = client()
        val call = client.newCall(get())
        val outcome = CompletableFuture<Any>()
        call.enqueue(outcome.asCallback())

        assertTrue(fetchStarted.await(5, TimeUnit.SECONDS), "fetch never started")
        call.cancel()
        releaseFetch.countDown()

        assertEquals("Canceled", assertIs<IOException>(outcome.get(10, TimeUnit.SECONDS)).message)
        assertEquals(0, gateway.requests.size)
        assertTrue(proceeded.isEmpty(), "a cancelled call was passed on after the fetch")
        assertEquals(200, client.call())
        assertEquals(1, firebase.fetches.get())
    }

    private fun CompletableFuture<Any>.asCallback() = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            complete(e)
        }

        override fun onResponse(call: Call, response: Response) {
            complete(response.use { it.code })
        }
    }

    /** 200 for [validKeys], [rejectionCode] otherwise; [fixedCode] and [redirectTo] override. */
    private class RecordingGateway : Dispatcher() {
        val validKeys: MutableList<String> = CopyOnWriteArrayList()
        val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

        @Volatile var rejectionCode = 401

        @Volatile var rejectionBody = "Unauthorized"

        @Volatile var fixedCode: Int? = null

        @Volatile var redirectTo: (RecordedRequest) -> okhttp3.HttpUrl? = { null }

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            redirectTo(request)?.let { target ->
                return MockResponse.Builder().code(302).addHeader("Location", target.toString()).build()
            }
            val code = fixedCode ?: if (request.headers[TtcGateway.API_KEY_HEADER] in validKeys) 200 else rejectionCode
            return MockResponse.Builder().code(code).body(if (code == 200) "[]" else rejectionBody).build()
        }
    }

    private companion object {
        const val STOP_URL = "https://ttc-gateway.invalid/api/v2/stops/1:970?locale=en"
        const val REMOTE_KEY = "sentinel-gateway-key-0001"
        const val OLD_KEY = "sentinel-old-key"
        const val NEW_KEY = "sentinel-new-key"
        const val FALLBACK_KEY = "sentinel-fallback-key"
    }
}
