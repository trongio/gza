package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.firebase.FirebaseRest
import okhttp3.logging.HttpLoggingInterceptor

/** Debug HTTP logging that can never print a secret. */
object TtcHttpLogging {
    /** Every header that carries a secret on the gateway or Firebase clients. */
    val REDACTED_HEADERS: List<String> = listOf(
        TtcGateway.API_KEY_HEADER,
        FirebaseRest.GOOG_API_KEY_HEADER,
        FirebaseRest.AUTHORIZATION_HEADER
    )

    /** The Firebase REST APIs also accept the API key as `?key=`. */
    val REDACTED_QUERY_PARAMS: List<String> = listOf("key")

    /**
     * Headers only, never bodies: the Remote Config response body contains the gateway key,
     * the Installations body contains tokens, and gateway bodies are large.
     */
    fun interceptor(logger: HttpLoggingInterceptor.Logger): HttpLoggingInterceptor =
        HttpLoggingInterceptor(logger).apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            REDACTED_HEADERS.forEach(::redactHeader)
            REDACTED_QUERY_PARAMS.forEach { redactQueryParams(it) }
        }
}
