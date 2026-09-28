package ge.hackerman.gza.core.ttc.http

import java.time.Duration
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * The two OkHttp clients. Both derive from one base client, so they share its connection
 * pool and dispatcher.
 */
object TtcHttpClients {
    val FIREBASE_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
    val FIREBASE_READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    val FIREBASE_WRITE_TIMEOUT: Duration = Duration.ofSeconds(10)
    val FIREBASE_CALL_TIMEOUT: Duration = Duration.ofSeconds(15)

    val GATEWAY_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    val GATEWAY_READ_TIMEOUT: Duration = Duration.ofSeconds(20)
    val GATEWAY_WRITE_TIMEOUT: Duration = Duration.ofSeconds(20)

    /** Covers a cold config fetch (two Firebase calls of up to 15 s each) plus one retry. */
    val GATEWAY_CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

    /** For Firebase only. It never carries [GatewayAuthInterceptor], so the gateway key cannot reach Google. */
    fun firebaseClient(base: OkHttpClient, logging: Interceptor?): OkHttpClient = base.newBuilder()
        .connectTimeout(FIREBASE_CONNECT_TIMEOUT)
        .readTimeout(FIREBASE_READ_TIMEOUT)
        .writeTimeout(FIREBASE_WRITE_TIMEOUT)
        .callTimeout(FIREBASE_CALL_TIMEOUT)
        .apply { logging?.let(::addInterceptor) }
        .build()

    /**
     * Logging goes after auth, so it sees the final URL and the `x-api-key` header (which
     * it redacts) and logs the retry too.
     */
    fun gatewayClient(base: OkHttpClient, auth: GatewayAuthInterceptor, logging: Interceptor?): OkHttpClient =
        base.newBuilder()
            .connectTimeout(GATEWAY_CONNECT_TIMEOUT)
            .readTimeout(GATEWAY_READ_TIMEOUT)
            .writeTimeout(GATEWAY_WRITE_TIMEOUT)
            .callTimeout(GATEWAY_CALL_TIMEOUT)
            .addInterceptor(auth)
            .apply { logging?.let(::addInterceptor) }
            .build()
}
