package ge.hackerman.gza.core.ttc.config

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfigSource
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache, then Remote Config, then stale cache, then the build-time fallback.
 *
 * Must be a singleton: one [Mutex] serializes every load and refresh, so 50 parallel
 * requests on a cold start cause one fetch, and parallel 401s cause one refetch.
 *
 * Stale while revalidate: once any usable config exists (active, or an expired one in the
 * cache), [current] returns it at once and refreshes on [refreshScope], one refresh at a
 * time. Requests never wait on a Firebase round trip just because a TTL or backoff ran out;
 * only a cold start with nothing usable, or a 401, waits.
 */
class DefaultGatewayConfigProvider(
    private val cache: TtcConfigCache,
    private val remote: RemoteGatewayConfigSource,
    fallback: TtcFallbackConfig,
    private val clock: Clock,
    private val refreshScope: CoroutineScope,
    policy: GatewayConfigPolicy = GatewayConfigPolicy()
) : GatewayConfigProvider {
    private val rules = GatewayConfigRules(policy, fallback)
    private val mutex = Mutex()
    private val revalidating = AtomicBoolean(false)

    // The two fields peek() reads; everything else is only touched under the mutex.
    @Volatile private var active: GatewayConfig? = null

    @Volatile private var nextRemoteAttemptAt: Instant = Instant.MIN
    private var consecutiveFailures = 0
    private var lastForcedRefreshAt: Instant? = null
    private var generation = 0L

    override fun peek(): GatewayConfig? = active?.takeIf { isFresh(it, clock.instant()) }

    override suspend fun current(): GatewayConfig {
        val stale = active
        return when {
            stale == null -> mutex.withLock { active ?: load() }
            isFresh(stale, clock.instant()) -> stale
            else -> stale.also { revalidateInBackground() }
        }
    }

    // ATOMIC start: the job runs its finally even if the scope is already cancelled, so
    // the flag can never stay stuck and block every later refresh.
    private fun revalidateInBackground() {
        if (!revalidating.compareAndSet(false, true)) return
        refreshScope.launch(start = CoroutineStart.ATOMIC) {
            try {
                mutex.withLock { if (peek() == null) load() }
            } catch (expected: IOException) {
                // A failed cache write or no key anywhere: the active config stays in use,
                // and the next call after the backoff tries again.
            } finally {
                revalidating.set(false)
            }
        }
    }

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
        val cached = if (active == null) rules.fromCache(readCacheOrNull()) else null
        return when {
            cached != null && rules.isWithinTtl(cached.fetchedAt, now) -> activate(cached, ConfigSource.CACHE)

            // Cold start over an expired cache: serve it now, refresh behind it.
            cached != null -> activate(cached, ConfigSource.STALE_CACHE).also { revalidateInBackground() }

            now < nextRemoteAttemptAt -> staleOrFallbackOrThrow()

            else -> fetchRemote(now) ?: staleOrFallbackOrThrow()
        }
    }

    private suspend fun fetchRemote(now: Instant): GatewayConfig? {
        val fetched = bestEffort("Remote Config fetch") { remote.fetch() }
        val apiKey = rules.usableKey(fetched?.apiKey)
        if (apiKey == null) {
            recordRemoteFailure(now)
            return null
        }
        val baseUrl = rules.baseUrlOrFallback(fetched?.baseUrl)
        consecutiveFailures = 0
        nextRemoteAttemptAt = Instant.MIN
        // The fetched key is good even if it cannot be persisted: use it now, and the next
        // launch simply fetches again.
        bestEffort("Config cache write") { cache.writeConfig(CachedGatewayConfig(baseUrl.toString(), apiKey, now)) }
        return activate(ConfigCandidate(baseUrl, apiKey, now), ConfigSource.REMOTE)
    }

    // An unreadable cache is a cache miss: Remote Config and the fallback still work.
    private suspend fun readCacheOrNull(): CachedGatewayConfig? = bestEffort("Config cache read") { cache.readConfig() }

    private fun recordRemoteFailure(now: Instant) {
        consecutiveFailures++
        nextRemoteAttemptAt = now + rules.backoff(consecutiveFailures)
    }

    /**
     * Runs [block], turning any failure into null. Cancellation of the caller still
     * propagates, but a CancellationException the block throws on its own (an inner
     * timeout) is a failure like any other. Only the class name is logged: messages from
     * the network or disk layers could carry URLs or tokens.
     */
    @Suppress("TooGenericExceptionCaught") // Whatever the source or cache throws is a failed attempt.
    private suspend fun <T> bestEffort(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        logFailure(what, e)
        null
    } catch (expected: RemoteConfigException) {
        // The normal way Remote Config fails; counted by the caller, not worth a log line.
        null
    } catch (e: Exception) {
        logFailure(what, e)
        null
    }

    // A key fetched from Remote Config is newer than the one baked at build time; if it has
    // since rotated, the 401 path fixes it. The fallback is never written to the cache, so
    // the next launch tries Remote Config again.
    private fun staleOrFallbackOrThrow(): GatewayConfig {
        val current = active
        val stale = current
            ?.takeIf { it.source != ConfigSource.FALLBACK }
            ?.let { ConfigCandidate(it.baseUrl, it.apiKey, it.fetchedAt) }
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

    private fun logFailure(what: String, e: Throwable) {
        logger.warning("$what failed: ${e.javaClass.name}")
    }

    private companion object {
        val logger: Logger = Logger.getLogger(DefaultGatewayConfigProvider::class.java.name)
    }
}
