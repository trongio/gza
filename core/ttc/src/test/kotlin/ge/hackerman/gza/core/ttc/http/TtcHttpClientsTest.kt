package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Test

class TtcHttpClientsTest {
    private val base = OkHttpClient()
    private val auth = GatewayAuthInterceptor(
        object : GatewayConfigProvider {
            override fun peek(): GatewayConfig? = null

            override suspend fun current(): GatewayConfig = error("not called")

            override suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig? = null
        }
    )
    private val logging = TtcHttpLogging.interceptor {}

    @Test
    fun `gateway client runs auth before logging`() {
        val client = TtcHttpClients.gatewayClient(base, auth, logging)
        assertEquals(2, client.interceptors.size)
        assertSame(auth, client.interceptors[0])
        assertIs<HttpLoggingInterceptor>(client.interceptors[1])
        assertTrue(client.networkInterceptors.isEmpty())
    }

    @Test
    fun `gateway client without logging has only auth`() {
        assertEquals(listOf(auth), TtcHttpClients.gatewayClient(base, auth, null).interceptors)
    }

    @Test
    fun `gateway timeouts`() {
        val client = TtcHttpClients.gatewayClient(base, auth, null)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
        assertEquals(20_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.callTimeoutMillis)
    }

    @Test
    fun `gateway client never follows redirects so the key cannot leave the gateway host`() {
        val client = TtcHttpClients.gatewayClient(base, auth, null)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun `firebase client never carries the gateway auth interceptor`() {
        val client = TtcHttpClients.firebaseClient(base, logging)
        assertTrue(client.interceptors.none { it is GatewayAuthInterceptor })
        assertEquals(1, client.interceptors.size)
        assertTrue(TtcHttpClients.firebaseClient(base, null).interceptors.isEmpty())
    }

    @Test
    fun `firebase timeouts`() {
        val client = TtcHttpClients.firebaseClient(base, null)
        assertEquals(5_000, client.connectTimeoutMillis)
        assertEquals(10_000, client.readTimeoutMillis)
        assertEquals(10_000, client.writeTimeoutMillis)
        assertEquals(15_000, client.callTimeoutMillis)
    }

    @Test
    fun `both clients share the base connection pool and dispatcher`() {
        val firebase = TtcHttpClients.firebaseClient(base, null)
        val gateway = TtcHttpClients.gatewayClient(base, auth, null)
        assertSame(base.connectionPool, firebase.connectionPool)
        assertSame(base.connectionPool, gateway.connectionPool)
        assertSame(base.dispatcher, firebase.dispatcher)
        assertSame(base.dispatcher, gateway.dispatcher)
    }
}
