package ge.hackerman.gza.core.ttc.config

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object GatewayKeys {
    private const val MAX_KEY_LENGTH = 256
    private const val FIRST_VISIBLE_ASCII = '!'
    private const val LAST_VISIBLE_ASCII = '~'

    /**
     * `Request.Builder.header()` throws on control or non-ASCII characters, which inside an
     * interceptor would crash the call instead of failing it. A key that fails this check is
     * treated as absent, whatever its source.
     */
    fun isUsableApiKey(key: String?): Boolean = isHeaderSafe(key) && key.length <= MAX_KEY_LENGTH

    /** Non-empty visible ASCII only, so `Request.Builder.header()` can never throw on it. */
    @OptIn(ExperimentalContracts::class)
    fun isHeaderSafe(value: String?): Boolean {
        contract { returns(true) implies (value != null) }
        return value != null && value.isNotEmpty() && value.all { it in FIRST_VISIBLE_ASCII..LAST_VISIBLE_ASCII }
    }

    /** A gateway base URL, or null when it is not one we are willing to send the key to. */
    fun parseBaseUrl(raw: String?, requireHttps: Boolean): HttpUrl? {
        val url = raw?.trim()?.toHttpUrlOrNull() ?: return null
        val acceptable = (!requireHttps || url.isHttps) &&
            url.encodedQuery == null &&
            url.encodedFragment == null
        return url.takeIf { acceptable }
    }
}
