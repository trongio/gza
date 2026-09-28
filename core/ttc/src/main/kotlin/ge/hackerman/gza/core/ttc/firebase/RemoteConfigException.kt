package ge.hackerman.gza.core.ttc.firebase

/**
 * Remote Config could not supply a usable gateway key. The message is built only from
 * [reason], [httpCode] and [googleStatus], never from a body, URL or header, because the
 * fetch response carries the key. Not an IOException, so it cannot escape an interceptor
 * by accident.
 */
class RemoteConfigException(
    val reason: Reason,
    val httpCode: Int? = null,
    val googleStatus: String? = null,
    cause: Throwable? = null
) : Exception(buildMessage(reason, httpCode, googleStatus), cause) {
    enum class Reason { NOT_CONFIGURED, NETWORK, HTTP, MALFORMED, MISSING_KEY }

    private companion object {
        fun buildMessage(reason: Reason, httpCode: Int?, googleStatus: String?): String = buildString {
            append("Remote Config failed: ").append(reason)
            httpCode?.let { append(" http=").append(it) }
            googleStatus?.let { append(" status=").append(it) }
        }
    }
}
