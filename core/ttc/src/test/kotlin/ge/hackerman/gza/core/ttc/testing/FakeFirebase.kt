package ge.hackerman.gza.core.ttc.testing

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/** Routes Installations and Remote Config calls by path and counts them per endpoint. */
class FakeFirebase(private val projectId: String = FirebaseFixtures.PROJECT_ID) : Dispatcher() {
    val installations = AtomicInteger()
    val tokenRefreshes = AtomicInteger()
    val fetches = AtomicInteger()
    val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    @Volatile var onInstall: (RecordedRequest) -> MockResponse = {
        FirebaseFixtures.json(200, FirebaseFixtures.read("installation-ok.json"))
    }

    @Volatile var onRefresh: (RecordedRequest) -> MockResponse = {
        FirebaseFixtures.json(200, FirebaseFixtures.read("auth-token-ok.json"))
    }

    @Volatile var onFetch: (RecordedRequest) -> MockResponse = {
        FirebaseFixtures.json(200, FirebaseFixtures.fetchOk())
    }

    fun serveKey(key: String, baseUrl: String? = FirebaseFixtures.PRODUCTION_BASE_URL, headersDelayMs: Long = 0) {
        onFetch = { FirebaseFixtures.json(200, FirebaseFixtures.fetchOk(key, baseUrl), headersDelayMs) }
    }

    fun failFetch(code: Int) {
        onFetch = { FirebaseFixtures.json(code, """{"error":{"code":$code,"status":"UNAVAILABLE"}}""") }
    }

    fun requestsTo(suffix: String): List<RecordedRequest> = requests.filter { it.url.encodedPath.endsWith(suffix) }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val path = request.url.encodedPath
        val installationsPath = "/v1/projects/$projectId/installations"
        return when {
            path == installationsPath -> {
                installations.incrementAndGet()
                onInstall(request)
            }

            path.startsWith("$installationsPath/") && path.endsWith("/authTokens:generate") -> {
                tokenRefreshes.incrementAndGet()
                onRefresh(request)
            }

            path == "/v1/projects/$projectId/namespaces/firebase:fetch" -> {
                fetches.incrementAndGet()
                onFetch(request)
            }

            else -> MockResponse.Builder().code(404).build()
        }
    }
}
