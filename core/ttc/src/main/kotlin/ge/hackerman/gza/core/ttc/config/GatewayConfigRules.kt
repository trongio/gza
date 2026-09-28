package ge.hackerman.gza.core.ttc.config

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.http.TtcGateway
import java.time.Duration
import java.time.Instant
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Credentials that passed validation, before they become an active [GatewayConfig]. */
internal class ConfigCandidate(val baseUrl: HttpUrl, val apiKey: String, val fetchedAt: Instant?)

/** The stateless validation, TTL and backoff rules of [DefaultGatewayConfigProvider]. */
internal class GatewayConfigRules(private val policy: GatewayConfigPolicy, private val fallback: TtcFallbackConfig) {
    private val fallbackBaseUrl: HttpUrl =
        GatewayKeys.parseBaseUrl(fallback.gatewayBaseUrl, policy.requireHttpsBaseUrl)
            ?: TtcGateway.DEFAULT_BASE_URL.toHttpUrl()

    /** A remote or cached base URL we will not send the key to is replaced by the fallback one. */
    fun baseUrlOrFallback(raw: String?): HttpUrl =
        GatewayKeys.parseBaseUrl(raw, policy.requireHttpsBaseUrl) ?: fallbackBaseUrl

    fun usableKey(raw: String?): String? = raw?.trim()?.takeIf(GatewayKeys::isUsableApiKey)

    fun fromCache(cached: CachedGatewayConfig?): ConfigCandidate? {
        val apiKey = usableKey(cached?.apiKey) ?: return null
        return ConfigCandidate(baseUrlOrFallback(cached?.baseUrl), apiKey, cached?.fetchedAt)
    }

    fun fallbackCandidate(): ConfigCandidate? = usableKey(fallback.gatewayKey)?.let {
        ConfigCandidate(fallbackBaseUrl, it, null)
    }

    /**
     * A fetchedAt in the future (the clock moved back, or a corrupt cache) is not fresh:
     * it could otherwise stay "fresh" for as long as the skew. Duration.between cannot
     * overflow the way fetchedAt + ttl can near Instant.MAX.
     */
    fun isWithinTtl(fetchedAt: Instant?, now: Instant): Boolean =
        fetchedAt != null && !fetchedAt.isAfter(now) && Duration.between(fetchedAt, now) < policy.ttl

    fun isInForcedRefreshCooldown(lastForcedRefreshAt: Instant?, now: Instant): Boolean =
        lastForcedRefreshAt != null && now < lastForcedRefreshAt + policy.minForcedRefreshInterval

    /** initial * 2^(failures - 1), capped at the policy maximum. */
    fun backoff(failures: Int): Duration {
        val doublings = (failures - 1).coerceIn(0, MAX_DOUBLINGS)
        return minOf(policy.failureBackoffInitial.multipliedBy(1L shl doublings), policy.failureBackoffMax)
    }

    private companion object {
        // Only guards the shift; the cap is reached long before.
        const val MAX_DOUBLINGS = 30
    }
}
