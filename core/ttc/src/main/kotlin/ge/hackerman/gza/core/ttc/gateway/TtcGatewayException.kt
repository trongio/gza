package ge.hackerman.gza.core.ttc.gateway

import java.io.IOException

/**
 * The only exception [TtcGatewayClient] throws (besides cancellation and programming errors).
 * Messages come from status codes, class names and the gateway's problem title and detail
 * only: never a URL, header or body, so they are safe to log.
 */
sealed class TtcGatewayException(message: String, cause: Throwable?) : Exception(message, cause) {
    /** No usable key anywhere (Remote Config, cache, build fallback). Retry later; do not hammer. */
    class NoKey(cause: IOException) : TtcGatewayException("Gateway key unavailable", cause)

    /** Connection, DNS, timeout, or a config fetch failure wrapped by the interceptor. */
    class Network(cause: IOException) :
        TtcGatewayException(
            "Gateway unreachable: ${cause.javaClass.simpleName}",
            cause
        )

    /**
     * Non-2xx. A 500 with a text body can mean an unknown id (TTC_API.md), so it is no reason
     * to delete cached data. A 401 here means the retry with fresh credentials failed too.
     */
    class Http(val code: Int, val problem: GatewayProblem?) :
        TtcGatewayException(
            "Gateway HTTP $code" + (problem?.detail ?: problem?.title)?.let { ": $it" }.orEmpty(),
            null
        )

    /** A 2xx body that is not the expected JSON shape, is empty, or lacks the one required identity. */
    class Malformed(cause: Throwable?) : TtcGatewayException("Gateway response malformed", cause)
}

/** The useful part of an RFC 7807 error body. */
data class GatewayProblem(val title: String?, val detail: String?)
