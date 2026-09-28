package ge.hackerman.gza.di

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import ge.hackerman.gza.core.ttc.http.GatewayAuthInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Test

class NetworkModuleTest {
    private val base = OkHttpClient()
    private val logger = HttpLoggingInterceptor.Logger { }
    private val auth = GatewayAuthInterceptor(
        object : GatewayConfigProvider {
            override fun peek(): GatewayConfig? = null

            override suspend fun current(): GatewayConfig = error("not called")

            override suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig? = null
        }
    )

    @Test
    fun `logging comes after auth so it sees and redacts the key header`() {
        val interceptors = buildGatewayClient(base, auth, debug = true, logger).interceptors
        assertEquals(2, interceptors.size)
        assertSame(auth, interceptors[0])
        val logging = assertIs<HttpLoggingInterceptor>(interceptors[1])
        assertEquals(HttpLoggingInterceptor.Level.HEADERS, logging.level)
    }

    @Test
    fun `release builds add no logging interceptor`() {
        assertEquals(listOf(auth), buildGatewayClient(base, auth, debug = false, logger).interceptors)
        assertTrue(buildFirebaseClient(base, debug = false, logger).interceptors.isEmpty())
    }

    @Test
    fun `debug firebase client logs headers only`() {
        val logging =
            assertIs<HttpLoggingInterceptor>(buildFirebaseClient(base, debug = true, logger).interceptors.single())
        assertEquals(HttpLoggingInterceptor.Level.HEADERS, logging.level)
    }

    @Test
    fun `firebase credentials come from the fallback config`() {
        val fallback =
            TtcFallbackConfig("https://example.com", "sentinel-key", "sentinel-fb", "test-project", "sentinel-app")
        assertEquals("test-project", NetworkModule.provideFirebaseCredentials(fallback).projectId)
    }

    @Test
    fun `io dispatcher is provided`() {
        assertEquals(kotlinx.coroutines.Dispatchers.IO, CoroutinesModule.provideIoDispatcher())
    }
}
