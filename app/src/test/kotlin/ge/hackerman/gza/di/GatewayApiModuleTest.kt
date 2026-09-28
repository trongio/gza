package ge.hackerman.gza.di

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class GatewayApiModuleTest {
    @Test
    fun `the client is built without touching the network`() {
        val calls = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet()
            it.proceed(it.request())
        }.build()
        val clock = Clock.fixed(Instant.parse("2026-09-28T16:43:32Z"), ZoneOffset.UTC)
        assertNotNull(GatewayApiModule.provideTtcGatewayClient(http, clock))
        assertEquals(0, calls.get())
    }
}
