package ge.hackerman.gza.core.ttc.testing

import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClientFactory
import ge.hackerman.gza.core.ttc.http.GatewayAuthInterceptor
import ge.hackerman.gza.core.ttc.http.TtcHttpClients
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient

/**
 * A MockWebServer playing the gateway under `/pis-gateway`, answering with recorded
 * fixtures (status and content type from their sidecars), behind the real auth interceptor
 * and a sentinel key.
 */
class FixtureGateway : AutoCloseable {
    val server = MockWebServer()
    val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    @Volatile private var respond: (RecordedRequest) -> MockResponse = { MockResponse.Builder().code(404).build() }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return respond(request)
            }
        }
        server.start()
    }

    val baseUrl get() = server.url("/pis-gateway")

    /** Answers every request with the recorded fixture at [path]; returns its sidecar. */
    fun serve(path: String): FixtureMeta {
        val meta = Fixtures.meta(path)
        val body = Fixtures.text(path)
        respond = { response(meta.status, body, meta.contentType) }
        return meta
    }

    fun respondWith(block: (RecordedRequest) -> MockResponse) {
        respond = block
    }

    fun client(
        clockAt: Instant = MutableClock.START,
        rotatedKey: String? = null,
        failure: IOException? = null
    ): TtcGatewayClient {
        val provider = StaticGatewayConfigProvider(baseUrl, FirebaseFixtures.GATEWAY_KEY, rotatedKey, failure)
        val http = TtcHttpClients.gatewayClient(OkHttpClient(), GatewayAuthInterceptor(provider), null)
        return TtcGatewayClientFactory.create(http, MutableClock(clockAt), validateEagerly = true)
    }

    fun single(): RecordedRequest = requests.single()

    override fun close() {
        server.close()
    }

    companion object {
        fun response(code: Int, body: String, contentType: String?): MockResponse = MockResponse.Builder()
            .code(code)
            .apply { if (!contentType.isNullOrEmpty()) addHeader("Content-Type", contentType) }
            .body(body)
            .build()
    }
}
