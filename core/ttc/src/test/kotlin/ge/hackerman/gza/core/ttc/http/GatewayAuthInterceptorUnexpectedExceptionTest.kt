package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.config.DefaultGatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.InMemoryTtcConfigCache
import ge.hackerman.gza.core.ttc.testing.FakeRemoteGatewayConfigSource
import ge.hackerman.gza.core.ttc.testing.MutableClock
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * What an enqueued gateway call (how Retrofit suspend functions run) does when loading the
 * config throws something that is not an IOException. The config load is on the call's
 * path on a cold start with nothing cached.
 */
class GatewayAuthInterceptorUnexpectedExceptionTest {
    private val remote = FakeRemoteGatewayConfigSource().apply {
        behavior = { throw IllegalStateException("boom") }
    }

    @Disabled(
        "Bug: DefaultGatewayConfigProvider.fetchRemote only catches RemoteConfigException, and " +
            "GatewayAuthInterceptor lets anything else through. OkHttp's AsyncCall reports an " +
            "IOException to the callback and then rethrows the original on its dispatcher " +
            "thread, which on Android is a process crash."
    )
    @Test
    fun `a runtime exception while loading the config fails the enqueued call without an uncaught exception`() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val clock = MutableClock()
            val provider = DefaultGatewayConfigProvider(
                InMemoryTtcConfigCache(),
                remote,
                TtcFallbackConfig("https://fallback.example.com/pis-gateway", "sentinel-fallback-key", "", "", ""),
                clock,
                scope
            )
            val client = TtcHttpClients.gatewayClient(OkHttpClient(), GatewayAuthInterceptor(provider), null)
            val failure = CompletableFuture<IOException>()
            client.newCall(Request.Builder().url("${TtcGateway.PLACEHOLDER_BASE_URL}api/v2/stops").build())
                .enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            failure.complete(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.close()
                            failure.completeExceptionally(AssertionError("unexpected response ${response.code}"))
                        }
                    }
                )
            assertIs<IOException>(failure.get(TIMEOUT_S, TimeUnit.SECONDS))
            // The rethrow happens right after onFailure on the same thread.
            Thread.sleep(SETTLE_MS)
            assertTrue(uncaught.isEmpty(), "reached the uncaught handler: $uncaught")
        } finally {
            scope.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    private companion object {
        const val TIMEOUT_S = 10L
        const val SETTLE_MS = 500L
    }
}
