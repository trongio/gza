package ge.hackerman.gza.core.ttc.config

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfigSource
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache, then Remote Config, then stale cache, then the build-time fallback.
 *
 * Must be a singleton: one [Mutex] serializes every load and refresh, so 50 parallel
 * requests on a cold start cause one fetch, and parallel 401s cause one refetch.
 */
class DefaultGatewayConfigProvider(
    private val cache: TtcConfigCache,
    private val remote: RemoteGatewayConfigSource,
    fallback: TtcFallbackConfig,
    private val clock: Clock,
    policy: GatewayConfigPolicy = GatewayConfigPolicy()
) : GatewayConfigProvider {
    private val rules = GatewayConfigRules(policy, fallback)
    private val mutex = Mutex()

    // The two fields peek() reads; everything else is only touched under the mutex.
    @Volatile private var active: GatewayConfig? = null

    @Volatile private var nextRemoteAttemptAt: Instant = Instant.MIN
    private var consecutiveFailures = 0
    private var lastForcedRefreshAt: Instant? = null
    private var generation = 0L

    override fun peek(): GatewayConfig? = active?.takeIf { isFresh(it, clock.instant()) }

    override suspend fun current(): GatewayConfig = peek() ?: mutex.withLock { peek() ?: load() }

    override suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig? = mutex.withLock {
        val now = clock.instant()
        val current = active
        when {
            // Another request already refreshed after the same rejection: reuse its result.
            current != null && current.generation != rejected.generation && !current.sameCredentialsAs(rejected) ->
                current

            // A key that is really dead must not cause one refetch per request.
            rules.isInForcedRefreshCooldown(lastForcedRefreshAt, now) -> null

            else -> forceRefresh(rejected, now)
        }
    }

    // A 401 is strong evidence of rotation, so this ignores the failure backoff.
    private suspend fun forceRefresh(rejected: GatewayConfig, now: Instant): GatewayConfig? {
        lastForcedRefreshAt = now
        val fresh = fetchRemote(now)
        return if (fresh != null) {
            // Same key again means the 401 is not a rotation: retrying would only repeat it.
            fresh.takeUnless { it.sameCredentialsAs(rejected) }
        } else {
            rules.fallbackCandidate()
                ?.takeUnless { it.apiKey == rejected.apiKey && it.baseUrl == rejected.baseUrl }
                ?.let { activate(it, ConfigSource.FALLBACK) }
        }
    }

    private suspend fun load(): GatewayConfig {
        val now = clock.instant()
        val staleCandidate = if (active == null) rules.fromCache(cache.readConfig()) else null
        return when {
            staleCandidate != null && rules.isWithinTtl(staleCandidate.fetchedAt, now) ->
                activate(staleCandidate, ConfigSource.CACHE)

            now < nextRemoteAttemptAt -> staleOrFallbackOrThrow(staleCandidate)

            else -> fetchRemote(now) ?: staleOrFallbackOrThrow(staleCandidate)
        }
    }

    private suspend fun fetchRemote(now: Instant): GatewayConfig? {
        val fetched = try {
            remote.fetch()
        } catch (expected: RemoteConfigException) {
            // Counted as a failure below; the caller moves on to the stale cache or the fallback.
            null
        }
        val apiKey = rules.usableKey(fetched?.apiKey)
        if (apiKey == null) {
            consecutiveFailures++
            nextRemoteAttemptAt = now + rules.backoff(consecutiveFailures)
            return null
        }
        val baseUrl = rules.baseUrlOrFallback(fetched?.baseUrl)
        cache.writeConfig(CachedGatewayConfig(baseUrl.toString(), apiKey, now))
        consecutiveFailures = 0
        nextRemoteAttemptAt = Instant.MIN
        return activate(ConfigCandidate(baseUrl, apiKey, now), ConfigSource.REMOTE)
    }

    // A key fetched from Remote Config is newer than the one baked at build time; if it has
    // since rotated, the 401 path fixes it. The fallback is never written to the cache, so
    // the next launch tries Remote Config again.
    private fun staleOrFallbackOrThrow(staleCandidate: ConfigCandidate?): GatewayConfig {
        val current = active
        val stale = if (current != null && current.source != ConfigSource.FALLBACK) {
            ConfigCandidate(current.baseUrl, current.apiKey, current.fetchedAt)
        } else {
            staleCandidate
        }
        val fallbackCandidate = rules.fallbackCandidate()
        return when {
            stale != null -> activate(stale, ConfigSource.STALE_CACHE)

            fallbackCandidate != null -> activate(fallbackCandidate, ConfigSource.FALLBACK)

            else -> throw GatewayConfigUnavailableException(
                "No gateway key: Remote Config failed and no build-time fallback"
            )
        }
    }

    private fun isFresh(config: GatewayConfig, now: Instant): Boolean = when (config.source) {
        ConfigSource.REMOTE, ConfigSource.CACHE -> rules.isWithinTtl(config.fetchedAt, now)

        // Only good until the backoff allows the next remote attempt.
        ConfigSource.STALE_CACHE, ConfigSource.FALLBACK -> now < nextRemoteAttemptAt
    }

    private fun activate(candidate: ConfigCandidate, source: ConfigSource): GatewayConfig = GatewayConfig(
        baseUrl = candidate.baseUrl,
        apiKey = candidate.apiKey,
        source = source,
        fetchedAt = candidate.fetchedAt.takeUnless { source == ConfigSource.FALLBACK },
        generation = ++generation
    ).also { active = it }
}
