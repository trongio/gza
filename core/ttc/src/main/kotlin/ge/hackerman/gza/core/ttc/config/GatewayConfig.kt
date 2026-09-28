package ge.hackerman.gza.core.ttc.config

import java.io.IOException
import java.time.Duration
import java.time.Instant
import okhttp3.HttpUrl

/** Where the active gateway credentials came from. */
enum class ConfigSource { REMOTE, CACHE, STALE_CACHE, FALLBACK }

/** What the interceptor uses. [generation] increases on every activation inside one provider. */
class GatewayConfig(
    /** For example `https://transit.ttc.com.ge/pis-gateway`, without `/api`. */
    val baseUrl: HttpUrl,
    val apiKey: String,
    val source: ConfigSource,
    /** Null for [ConfigSource.FALLBACK]. */
    val fetchedAt: Instant?,
    val generation: Long
) {
    fun sameCredentialsAs(other: GatewayConfig): Boolean = apiKey == other.apiKey && baseUrl == other.baseUrl

    // Never a data class: a logged config must not print the key.
    override fun toString(): String = "GatewayConfig(baseUrl=$baseUrl, apiKey=<redacted>, source=$source, " +
        "fetchedAt=$fetchedAt, generation=$generation)"
}

data class GatewayConfigPolicy(
    /** The Firebase SDK's default minimum fetch interval. */
    val ttl: Duration = DEFAULT_TTL,
    /** A key that is really dead must not cause one refetch per request. */
    val minForcedRefreshInterval: Duration = DEFAULT_MIN_FORCED_REFRESH,
    val failureBackoffInitial: Duration = DEFAULT_BACKOFF_INITIAL,
    val failureBackoffMax: Duration = DEFAULT_BACKOFF_MAX,
    /** Tests pass false, because MockWebServer speaks plain http. */
    val requireHttpsBaseUrl: Boolean = true
)

private val DEFAULT_TTL: Duration = Duration.ofHours(12)
private val DEFAULT_MIN_FORCED_REFRESH: Duration = Duration.ofSeconds(60)
private val DEFAULT_BACKOFF_INITIAL: Duration = Duration.ofMinutes(1)
private val DEFAULT_BACKOFF_MAX: Duration = Duration.ofHours(1)

/** An [IOException] so OkHttp fails the call with it instead of crashing the interceptor. */
class GatewayConfigUnavailableException(message: String) : IOException(message)
