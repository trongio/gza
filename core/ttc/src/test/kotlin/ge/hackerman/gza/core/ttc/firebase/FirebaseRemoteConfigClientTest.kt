package ge.hackerman.gza.core.ttc.firebase

import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import ge.hackerman.gza.core.ttc.config.InMemoryTtcConfigCache
import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException.Reason
import ge.hackerman.gza.core.ttc.testing.FakeFirebase
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.APP_ID
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.AUTH_TOKEN
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.AUTH_TOKEN_2
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.FID
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.FIREBASE_KEY
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.GATEWAY_KEY
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.REFRESH_TOKEN
import ge.hackerman.gza.core.ttc.testing.MutableClock
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Real sockets, so the client runs on Dispatchers.IO and tests block with runBlocking.
class FirebaseRemoteConfigClientTest {
    private val server = MockWebServer()
    private val firebase = FakeFirebase()
    private val cache = InMemoryTtcConfigCache()
    private val clock = MutableClock()
    private var nextFid = "cGeneratedFid000000001"

    @BeforeEach
    fun setUp() {
        server.dispatcher = firebase
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun client(
        credentials: FirebaseWebCredentials = FirebaseFixtures.credentials,
        http: OkHttpClient = OkHttpClient()
    ) = FirebaseRemoteConfigClient(
        httpClient = http,
        endpoints = FirebaseEndpoints(server.url("/"), server.url("/")),
        credentials = credentials,
        cache = cache,
        clock = clock,
        ioDispatcher = Dispatchers.IO,
        newFid = { nextFid }
    )

    private fun fetch(client: FirebaseRemoteConfigClient = client()) = runBlocking { client.fetch() }

    private fun fetchFailure(client: FirebaseRemoteConfigClient = client()): RemoteConfigException {
        val error = assertFailsWith<RemoteConfigException> { fetch(client) }
        FirebaseFixtures.SECRETS.forEach { assertFalse(it in error.message.orEmpty(), "message leaks a secret") }
        return error
    }

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(checkNotNull(body).utf8()).jsonObject

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun fetchRequest(): RecordedRequest = firebase.requestsTo("firebase:fetch").single()

    // Request shapes

    @Test
    fun `installation request has the web sdk shape`() {
        fetch()
        val request = firebase.requestsTo("/installations").single()
        assertEquals("POST", request.method)
        assertEquals("/v1/projects/test-project/installations", request.url.encodedPath)
        assertEquals(FIREBASE_KEY, request.headers["x-goog-api-key"])
        val body = request.json()
        assertEquals(nextFid, body.string("fid"))
        assertEquals(APP_ID, body.string("appId"))
        assertEquals("FIS_v2", body.string("authVersion"))
        assertEquals("w:0.6.4", body.string("sdkVersion"))
    }

    @Test
    fun `fetch request uses the header key, the returned fid and its token`() {
        val config = fetch()
        val request = fetchRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/projects/test-project/namespaces/firebase:fetch", request.url.encodedPath)
        assertNull(request.url.queryParameter("key"))
        assertNull(request.url.encodedQuery)
        assertEquals(FIREBASE_KEY, request.headers["x-goog-api-key"])
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        val body = request.json()
        assertEquals(FID, body.string("appInstanceId"))
        assertEquals(AUTH_TOKEN, body.string("appInstanceIdToken"))
        assertEquals(APP_ID, body.string("appId"))
        assertEquals("0.4.0", body.string("sdkVersion"))
        assertEquals("en-US", body.string("languageCode"))
        assertEquals(GATEWAY_KEY, config.apiKey)
        assertEquals(FirebaseFixtures.PRODUCTION_BASE_URL, config.baseUrl)
    }

    @Test
    fun `recorded fetch fixture parses`() {
        firebase.onFetch = { FirebaseFixtures.json(200, FirebaseFixtures.read("fetch-ok.json")) }
        val config = fetch()
        assertEquals(GATEWAY_KEY, config.apiKey)
        assertEquals(FirebaseFixtures.PRODUCTION_BASE_URL, config.baseUrl)
    }

    @Test
    fun `missing base url entry is null, not an error`() {
        firebase.serveKey(GATEWAY_KEY, baseUrl = null)
        assertNull(fetch().baseUrl)
    }

    // Installation lifecycle

    @Test
    fun `second fetch reuses the stored installation`() {
        val client = client()
        fetch(client)
        fetch(client)
        assertEquals(1, firebase.installations.get())
        assertEquals(0, firebase.tokenRefreshes.get())
        assertEquals(2, firebase.fetches.get())
    }

    @Test
    fun `installation is stored in the cache`() {
        fetch()
        val stored = assertNotNull(runBlocking { cache.readInstallation() })
        assertEquals(FID, stored.fid)
        assertEquals(REFRESH_TOKEN, stored.refreshToken)
        assertEquals(AUTH_TOKEN, stored.authToken)
        assertEquals(clock.now + Duration.ofDays(7), stored.authTokenExpiresAt)
    }

    @Test
    fun `server assigned fid is stored and used`() {
        nextFid = "bad"
        fetch()
        assertEquals("bad", firebase.requestsTo("/installations").single().json().string("fid"))
        assertEquals(FID, runBlocking { cache.readInstallation() }?.fid)
        assertEquals(FID, fetchRequest().json().string("appInstanceId"))
    }

    @Test
    fun `expiring token is refreshed with the refresh token`() {
        seedInstallation(expiresIn = Duration.ofMinutes(30))
        fetch()
        val refresh = firebase.requestsTo("authTokens:generate").single()
        assertEquals("/v1/projects/test-project/installations/$FID/authTokens:generate", refresh.url.encodedPath)
        assertEquals("FIS_v2 $REFRESH_TOKEN", refresh.headers["Authorization"])
        assertEquals(FIREBASE_KEY, refresh.headers["x-goog-api-key"])
        val installation = refresh.json()["installation"]!!.jsonObject
        assertEquals(APP_ID, installation.string("appId"))
        assertEquals("w:0.6.4", installation.string("sdkVersion"))

        val stored = assertNotNull(runBlocking { cache.readInstallation() })
        assertEquals(AUTH_TOKEN_2, stored.authToken)
        assertEquals(clock.now + Duration.ofDays(7), stored.authTokenExpiresAt)
        assertEquals(AUTH_TOKEN_2, fetchRequest().json().string("appInstanceIdToken"))
        assertEquals(0, firebase.installations.get())
    }

    @Test
    fun `token valid for more than an hour is used as is`() {
        seedInstallation(expiresIn = Duration.ofHours(2))
        fetch()
        assertEquals(0, firebase.tokenRefreshes.get())
        assertEquals(0, firebase.installations.get())
        assertEquals("seeded-auth", fetchRequest().json().string("appInstanceIdToken"))
    }

    @Test
    fun `rejected refresh clears the installation and registers anew`() {
        seedInstallation(expiresIn = Duration.ofMinutes(30))
        firebase.onRefresh = { FirebaseFixtures.json(401, FirebaseFixtures.read("error-unauthenticated.json")) }
        fetch()
        assertEquals(1, firebase.tokenRefreshes.get())
        assertEquals(1, firebase.installations.get())
        assertEquals(FID, runBlocking { cache.readInstallation() }?.fid)
        assertEquals(AUTH_TOKEN, fetchRequest().json().string("appInstanceIdToken"))
    }

    @Test
    fun `failed refresh keeps a token that has not expired yet`() {
        seedInstallation(expiresIn = Duration.ofMinutes(30))
        firebase.onRefresh = { FirebaseFixtures.json(500, "{}") }
        fetch()
        assertEquals(0, firebase.installations.get())
        assertEquals("seeded-auth", fetchRequest().json().string("appInstanceIdToken"))
    }

    @Test
    fun `failed refresh of an expired token fetches without a token`() {
        seedInstallation(expiresIn = Duration.ofMinutes(-5))
        firebase.onRefresh = { FirebaseFixtures.json(500, "{}") }
        fetch()
        assertFalse("appInstanceIdToken" in fetchRequest().json())
    }

    @Test
    fun `installations failing still fetches without a token`() {
        firebase.onInstall = { FirebaseFixtures.json(500, "{}") }
        val config = fetch()
        assertEquals(GATEWAY_KEY, config.apiKey)
        val body = fetchRequest().json()
        assertFalse("appInstanceIdToken" in body)
        assertEquals(nextFid, body.string("appInstanceId"))
        assertNull(runBlocking { cache.readInstallation() })
    }

    @Test
    fun `installation response without tokens counts as a failure`() {
        firebase.onInstall = { FirebaseFixtures.json(200, """{"fid":"$FID"}""") }
        fetch()
        assertNull(runBlocking { cache.readInstallation() })
        assertFalse("appInstanceIdToken" in fetchRequest().json())
    }

    @Test
    fun `installation response with an unsafe refresh token counts as a failure`() {
        firebase.onInstall = {
            FirebaseFixtures.json(
                200,
                FirebaseFixtures.read("installation-ok.json").replace(REFRESH_TOKEN, "bad\\u0001token")
            )
        }
        assertEquals(GATEWAY_KEY, fetch().apiKey)
        assertNull(runBlocking { cache.readInstallation() })
        assertFalse("appInstanceIdToken" in fetchRequest().json())
    }

    @Test
    fun `stored refresh token unusable in a header is replaced by a new installation`() {
        listOf("bad\ntoken", "tok\u0000en", "ტოკენი", "").forEach { token ->
            runBlocking {
                cache.writeInstallation(
                    FirebaseInstallation(
                        FID,
                        token,
                        "seeded-auth",
                        clock.now + Duration.ofMinutes(30)
                    )
                )
            }
            firebase.installations.set(0)
            firebase.tokenRefreshes.set(0)
            assertEquals(GATEWAY_KEY, fetch().apiKey)
            assertEquals(0, firebase.tokenRefreshes.get())
            assertEquals(1, firebase.installations.get())
            assertEquals(REFRESH_TOKEN, runBlocking { cache.readInstallation() }?.refreshToken)
        }
    }

    // Error mapping

    @Test
    fun `api key unusable in a header is malformed, not a crash, and sends nothing`() {
        val error =
            fetchFailure(client(credentials = FirebaseWebCredentials("$FIREBASE_KEY\n", "test-project", APP_ID)))
        assertEquals(Reason.MALFORMED, error.reason)
        assertNull(error.cause, "the cause would quote the key")
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fetch 400 maps to http with the google status`() {
        firebase.onFetch = { FirebaseFixtures.json(400, FirebaseFixtures.read("error-invalid-argument.json")) }
        val error = fetchFailure()
        assertEquals(Reason.HTTP, error.reason)
        assertEquals(400, error.httpCode)
        assertEquals("INVALID_ARGUMENT", error.googleStatus)
        assertFalse("API key not valid" in error.message.orEmpty())
    }

    @Test
    fun `fetch 403 maps to permission denied`() {
        firebase.onFetch = { FirebaseFixtures.json(403, FirebaseFixtures.read("error-permission-denied.json")) }
        val error = fetchFailure()
        assertEquals(Reason.HTTP, error.reason)
        assertEquals(403, error.httpCode)
        assertEquals("PERMISSION_DENIED", error.googleStatus)
    }

    @Test
    fun `non json error body has no google status`() {
        firebase.onFetch = { MockResponse.Builder().code(503).body("Service Unavailable").build() }
        val error = fetchFailure()
        assertEquals(503, error.httpCode)
        assertNull(error.googleStatus)
    }

    @Test
    fun `no template means missing key`() {
        firebase.onFetch = { FirebaseFixtures.json(200, FirebaseFixtures.read("fetch-no-template.json")) }
        assertEquals(Reason.MISSING_KEY, fetchFailure().reason)
    }

    @Test
    fun `empty object means missing key`() {
        firebase.onFetch = { FirebaseFixtures.json(200, "{}") }
        assertEquals(Reason.MISSING_KEY, fetchFailure().reason)
    }

    @Test
    fun `blank key means missing key`() {
        firebase.serveKey("   ")
        assertEquals(Reason.MISSING_KEY, fetchFailure().reason)
    }

    @Test
    fun `key with a newline inside is malformed`() {
        firebase.serveKey("sentinel-gateway\nkey-0001")
        assertEquals(Reason.MALFORMED, fetchFailure().reason)
    }

    @Test
    fun `surrounding whitespace around the key is trimmed`() {
        firebase.serveKey("  $GATEWAY_KEY\n")
        assertEquals(GATEWAY_KEY, fetch().apiKey)
    }

    @Test
    fun `non json body is malformed`() {
        firebase.onFetch = { FirebaseFixtures.json(200, "not json") }
        assertEquals(Reason.MALFORMED, fetchFailure().reason)
    }

    @Test
    fun `closed server maps to network`() {
        val client = client()
        server.close()
        assertEquals(Reason.NETWORK, fetchFailure(client).reason)
    }

    @Test
    fun `read timeout maps to network`() {
        firebase.serveKey(GATEWAY_KEY, headersDelayMs = 1_000)
        val http = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        assertEquals(Reason.NETWORK, fetchFailure(client(http = http)).reason)
    }

    @Test
    fun `incomplete credentials fail without any request`() {
        val error = fetchFailure(client(credentials = FirebaseWebCredentials(FIREBASE_KEY, "test-project", " ")))
        assertEquals(Reason.NOT_CONFIGURED, error.reason)
        assertEquals(0, server.requestCount)
    }

    // expiresIn

    @Test
    fun `expires in parses decimal seconds and treats garbage as expired`() {
        assertEquals(Duration.ofDays(7), FirebaseRemoteConfigClient.parseExpiresIn("604800s"))
        assertEquals(Duration.ofMillis(3_500), FirebaseRemoteConfigClient.parseExpiresIn("3.5s"))
        assertEquals(Duration.ZERO, FirebaseRemoteConfigClient.parseExpiresIn("garbage"))
        assertEquals(Duration.ZERO, FirebaseRemoteConfigClient.parseExpiresIn(null))
        assertEquals(Duration.ZERO, FirebaseRemoteConfigClient.parseExpiresIn("-5s"))
        assertEquals(Duration.ofDays(365), FirebaseRemoteConfigClient.parseExpiresIn("99999999999999999999s"))
    }

    private fun seedInstallation(expiresIn: Duration) = runBlocking {
        cache.writeInstallation(FirebaseInstallation(FID, REFRESH_TOKEN, "seeded-auth", clock.now + expiresIn))
    }
}
