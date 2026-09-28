package ge.hackerman.gza.core.ttc.config

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfig
import ge.hackerman.gza.core.ttc.testing.FakeRemoteGatewayConfigSource
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.PRODUCTION_BASE_URL
import ge.hackerman.gza.core.ttc.testing.MutableClock
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGatewayConfigProviderTest {
    private val cache = InMemoryTtcConfigCache()
    private val remote = FakeRemoteGatewayConfigSource().apply { serve(REMOTE_KEY) }
    private val clock = MutableClock()
    private val policy = GatewayConfigPolicy()
    private var fallback = TtcFallbackConfig(
        gatewayBaseUrl = FALLBACK_BASE_URL,
        gatewayKey = FALLBACK_KEY,
        firebaseApiKey = "sentinel-firebase-key",
        firebaseProjectId = "test-project",
        firebaseAppId = "1:0:web:sentinel"
    )

    // Background refreshes run on the test scheduler: runCurrent() lets them finish.
    private fun TestScope.provider() =
        DefaultGatewayConfigProvider(cache, remote, fallback, clock, backgroundScope, policy)

    private suspend fun seedCache(key: String = CACHED_KEY, age: Duration = Duration.ZERO) {
        cache.writeConfig(CachedGatewayConfig(PRODUCTION_BASE_URL, key, clock.now - age))
    }

    // Loading

    @Test
    fun `cold start fetches once and writes the cache`() = runTest {
        val provider = provider()
        val config = provider.current()
        assertEquals(REMOTE_KEY, config.apiKey)
        assertEquals(ConfigSource.REMOTE, config.source)
        assertEquals(PRODUCTION_BASE_URL, config.baseUrl.toString())
        assertEquals(clock.now, config.fetchedAt)
        assertEquals(1, remote.fetches.get())
        assertEquals(CachedGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY, clock.now), cache.readConfig())
        assertSame(config, provider.current())
        assertEquals(1, remote.fetches.get())
    }

    @Test
    fun `fresh cache means no fetch`() = runTest {
        seedCache(age = Duration.ofHours(1))
        val config = provider().current()
        assertEquals(CACHED_KEY, config.apiKey)
        assertEquals(ConfigSource.CACHE, config.source)
        assertEquals(0, remote.fetches.get())
    }

    @Test
    fun `cache just inside the ttl is fresh`() = runTest {
        seedCache(age = policy.ttl - Duration.ofSeconds(1))
        assertEquals(ConfigSource.CACHE, provider().current().source)
        assertEquals(0, remote.fetches.get())
    }

    @Test
    fun `cache at the ttl is served stale and refetched in the background`() = runTest {
        seedCache(age = policy.ttl)
        val provider = provider()
        val stale = provider.current()
        assertEquals(CACHED_KEY, stale.apiKey)
        assertEquals(ConfigSource.STALE_CACHE, stale.source)
        runCurrent()
        assertEquals(1, remote.fetches.get())
        assertEquals(REMOTE_KEY, provider.peek()?.apiKey)
        assertEquals(ConfigSource.REMOTE, provider.current().source)
    }

    @Test
    fun `cache fetched in the future is stale and refetched`() = runTest {
        // The device clock moved back by a day since the fetch.
        seedCache(age = Duration.ofDays(-1))
        val provider = provider()
        assertEquals(ConfigSource.STALE_CACHE, provider.current().source)
        runCurrent()
        assertEquals(1, remote.fetches.get())
        assertEquals(REMOTE_KEY, provider.current().apiKey)
    }

    @Test
    fun `extreme fetched at values do not overflow`() = runTest {
        listOf(Instant.MAX, Instant.MIN).forEach { fetchedAt ->
            cache.writeConfig(CachedGatewayConfig(PRODUCTION_BASE_URL, CACHED_KEY, fetchedAt))
            val provider = provider()
            assertEquals(ConfigSource.STALE_CACHE, provider.current().source)
            runCurrent()
            assertEquals(REMOTE_KEY, provider.current().apiKey)
        }
    }

    @Test
    fun `ttl window is closed at fetch time and open at fetch plus ttl`() {
        val rules = GatewayConfigRules(policy, fallback)
        val now = clock.now
        assertTrue(rules.isWithinTtl(now, now))
        assertTrue(rules.isWithinTtl(now - policy.ttl + Duration.ofNanos(1), now))
        assertFalse(rules.isWithinTtl(now - policy.ttl, now))
        assertFalse(rules.isWithinTtl(now + Duration.ofNanos(1), now))
        assertFalse(rules.isWithinTtl(null, now))
        assertFalse(rules.isWithinTtl(Instant.MAX, now))
        assertFalse(rules.isWithinTtl(Instant.MIN, now))
    }

    @Test
    fun `active config expires after the ttl`() = runTest {
        val provider = provider()
        provider.current()
        clock.advanceBy(policy.ttl)
        assertNull(provider.peek())
        remote.serve("rotated-key")
        assertEquals(REMOTE_KEY, provider.current().apiKey)
        runCurrent()
        assertEquals("rotated-key", provider.current().apiKey)
        assertEquals(2, remote.fetches.get())
    }

    @Test
    fun `expired config is served at once while one refresh runs`() = runTest {
        seedCache(age = Duration.ofDays(1))
        val gate = CompletableDeferred<Unit>()
        remote.behavior = {
            gate.await()
            RemoteGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY)
        }
        val provider = provider()
        // Every call returns while the fetch is still blocked on the gate.
        val configs = List(20) { provider.current() }
        runCurrent()
        assertTrue(configs.all { it.apiKey == CACHED_KEY })
        assertEquals(1, remote.fetches.get())
        assertEquals(CACHED_KEY, provider.current().apiKey)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, remote.fetches.get())
        assertEquals(REMOTE_KEY, provider.current().apiKey)
    }

    @Test
    fun `a rejection during a background refresh waits for it and uses its result`() = runTest {
        seedCache(age = Duration.ofDays(1))
        val gate = CompletableDeferred<Unit>()
        remote.behavior = {
            gate.await()
            RemoteGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY)
        }
        val provider = provider()
        val stale = provider.current()
        runCurrent()
        val retry = async { provider.refreshAfterRejection(stale) }
        runCurrent()
        assertTrue(retry.isActive, "the 401 path must wait for the running refresh")
        gate.complete(Unit)
        assertEquals(REMOTE_KEY, retry.await()?.apiKey)
        assertEquals(1, remote.fetches.get())
    }

    @Test
    fun `failed background refresh keeps the stale config and backs off`() = runTest {
        seedCache(age = Duration.ofDays(1))
        remote.fail()
        val provider = provider()
        assertEquals(CACHED_KEY, provider.current().apiKey)
        runCurrent()
        assertEquals(1, remote.fetches.get())
        assertEquals(ConfigSource.STALE_CACHE, provider.peek()?.source)
        repeat(5) { provider.current() }
        runCurrent()
        assertEquals(1, remote.fetches.get())
        clock.advanceBy(Duration.ofMinutes(1))
        assertEquals(CACHED_KEY, provider.current().apiKey)
        runCurrent()
        assertEquals(2, remote.fetches.get())
    }

    @Test
    fun `concurrent cold start calls cause one fetch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.behavior = {
            gate.await()
            RemoteGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY)
        }
        val provider = provider()
        val calls = List(20) { async { provider.current() } }
        runCurrent()
        assertEquals(1, remote.fetches.get())
        gate.complete(Unit)
        val configs = calls.awaitAll()
        assertEquals(1, remote.fetches.get())
        assertTrue(configs.all { it.apiKey == REMOTE_KEY })
        assertEquals(1, configs.map { it.generation }.distinct().size)
    }

    @Test
    fun `unusable cached key is ignored`() = runTest {
        seedCache(key = "bad key")
        assertEquals(REMOTE_KEY, provider().current().apiKey)
    }

    // Failures

    @Test
    fun `remote failure with a stale cache uses it and keeps the cache`() = runTest {
        seedCache(age = Duration.ofDays(2))
        val before = cache.readConfig()
        remote.fail()
        val provider = provider()
        val config = provider.current()
        runCurrent()
        assertEquals(1, remote.fetches.get())
        assertEquals(CACHED_KEY, config.apiKey)
        assertEquals(ConfigSource.STALE_CACHE, config.source)
        assertEquals(ConfigSource.STALE_CACHE, provider.current().source)
        assertEquals(before, cache.readConfig())
    }

    @Test
    fun `remote failure without cache uses the fallback`() = runTest {
        remote.fail()
        val config = provider().current()
        assertEquals(FALLBACK_KEY, config.apiKey)
        assertEquals(ConfigSource.FALLBACK, config.source)
        assertEquals(FALLBACK_BASE_URL, config.baseUrl.toString())
        assertNull(config.fetchedAt)
        assertNull(cache.readConfig())
    }

    @Test
    fun `remote returning an unusable key counts as a failure`() = runTest {
        remote.serve("key with spaces")
        assertEquals(ConfigSource.FALLBACK, provider().current().source)
        assertNull(cache.readConfig())
    }

    @Test
    fun `no key anywhere throws an io exception`() = runTest {
        remote.fail()
        listOf("", "   ", "bad\nkey").forEach { key ->
            fallback = fallback.copy(gatewayKey = key)
            val error = assertFailsWith<GatewayConfigUnavailableException> { provider().current() }
            assertIs<IOException>(error)
        }
    }

    @Test
    fun `backoff doubles from one minute and resets on success`() = runTest {
        remote.fail()
        val provider = provider()
        provider.current()
        runCurrent()
        assertEquals(1, remote.fetches.get())

        clock.advanceBy(Duration.ofSeconds(59))
        assertEquals(ConfigSource.FALLBACK, provider.current().source)
        runCurrent()
        assertEquals(1, remote.fetches.get())

        clock.advanceBy(Duration.ofSeconds(1))
        provider.current()
        runCurrent()
        assertEquals(2, remote.fetches.get())

        clock.advanceBy(Duration.ofMinutes(2) - Duration.ofSeconds(1))
        provider.current()
        runCurrent()
        assertEquals(2, remote.fetches.get())
        clock.advanceBy(Duration.ofSeconds(1))
        provider.current()
        runCurrent()
        assertEquals(3, remote.fetches.get())

        remote.serve(REMOTE_KEY)
        clock.advanceBy(Duration.ofMinutes(4))
        // The call ending the backoff still gets the fallback; the refresh lands behind it.
        assertEquals(ConfigSource.FALLBACK, provider.current().source)
        runCurrent()
        assertEquals(ConfigSource.REMOTE, provider.peek()?.source)
        assertEquals(4, remote.fetches.get())

        // Reset: the next failure waits one minute again, not eight.
        clock.advanceBy(policy.ttl)
        remote.fail()
        provider.current()
        runCurrent()
        assertEquals(5, remote.fetches.get())
        clock.advanceBy(Duration.ofMinutes(1))
        provider.current()
        runCurrent()
        assertEquals(6, remote.fetches.get())
    }

    @Test
    fun `backoff is capped at one hour`() = runTest {
        remote.fail()
        val provider = provider()
        provider.current()
        runCurrent()
        // Waits: 1, 2, 4, 8, 16, 32 minutes, then 60 (capped) instead of 64.
        listOf(1L, 2, 4, 8, 16, 32).forEach {
            clock.advanceBy(Duration.ofMinutes(it))
            provider.current()
            runCurrent()
        }
        assertEquals(7, remote.fetches.get())
        clock.advanceBy(Duration.ofMinutes(59))
        provider.current()
        runCurrent()
        assertEquals(7, remote.fetches.get())
        clock.advanceBy(Duration.ofMinutes(1))
        provider.current()
        runCurrent()
        assertEquals(8, remote.fetches.get())
    }

    @Test
    fun `invalid remote base url uses the fallback base with the remote key`() = runTest {
        listOf("not a url", "http://transit.ttc.com.ge/pis-gateway", null).forEach { base ->
            remote.serve(REMOTE_KEY, baseUrl = base)
            val config = provider().current()
            assertEquals(REMOTE_KEY, config.apiKey)
            assertEquals(FALLBACK_BASE_URL, config.baseUrl.toString())
        }
    }

    @Test
    fun `http base url is accepted when https is not required`() = runTest {
        remote.serve(REMOTE_KEY, baseUrl = "http://127.0.0.1:1234/pis-gateway")
        val provider = DefaultGatewayConfigProvider(
            cache,
            remote,
            fallback,
            clock,
            backgroundScope,
            policy.copy(requireHttpsBaseUrl = false)
        )
        assertEquals("http://127.0.0.1:1234/pis-gateway", provider.current().baseUrl.toString())
    }

    // Refresh after rejection

    @Test
    fun `rejection with a rotated key returns it with a higher generation`() = runTest {
        val provider = provider()
        val first = provider.current()
        remote.serve("rotated-key")
        val refreshed = assertNotNull(provider.refreshAfterRejection(first))
        assertEquals("rotated-key", refreshed.apiKey)
        assertTrue(refreshed.generation > first.generation)
        assertEquals("rotated-key", cache.readConfig()?.apiKey)
        assertSame(refreshed, provider.peek())
    }

    @Test
    fun `rejection with the same key after refetch returns null`() = runTest {
        val provider = provider()
        val first = provider.current()
        assertNull(provider.refreshAfterRejection(first))
        assertEquals(2, remote.fetches.get())
    }

    @Test
    fun `second rejection within the cooldown returns null without fetching`() = runTest {
        val provider = provider()
        val first = provider.current()
        assertNull(provider.refreshAfterRejection(first))
        val active = assertNotNull(provider.peek())
        remote.serve("rotated-key")
        clock.advanceBy(Duration.ofSeconds(59))
        assertNull(provider.refreshAfterRejection(active))
        assertEquals(2, remote.fetches.get())

        clock.advanceBy(Duration.ofSeconds(1))
        assertEquals("rotated-key", provider.refreshAfterRejection(active)?.apiKey)
        assertEquals(3, remote.fetches.get())
    }

    @Test
    fun `stale rejection reuses the already refreshed config without fetching`() = runTest {
        val provider = provider()
        val first = provider.current()
        remote.serve("rotated-key")
        val refreshed = assertNotNull(provider.refreshAfterRejection(first))
        assertSame(refreshed, provider.refreshAfterRejection(first))
        assertEquals(2, remote.fetches.get())
    }

    @Test
    fun `rejection ignores the failure backoff`() = runTest {
        remote.fail()
        val provider = provider()
        val fallbackConfig = provider.current()
        remote.serve(REMOTE_KEY)
        assertEquals(REMOTE_KEY, provider.refreshAfterRejection(fallbackConfig)?.apiKey)
        assertEquals(2, remote.fetches.get())
    }

    @Test
    fun `remote down during refresh returns the fallback when it differs`() = runTest {
        val provider = provider()
        val first = provider.current()
        remote.fail()
        val refreshed = assertNotNull(provider.refreshAfterRejection(first))
        assertEquals(FALLBACK_KEY, refreshed.apiKey)
        assertEquals(ConfigSource.FALLBACK, refreshed.source)
    }

    @Test
    fun `remote down during refresh of the fallback itself returns null`() = runTest {
        remote.fail()
        val provider = provider()
        val fallbackConfig = provider.current()
        clock.advanceBy(Duration.ofMinutes(5))
        assertNull(provider.refreshAfterRejection(fallbackConfig))
    }

    // peek

    @Test
    fun `peek is null before anything is loaded and after the ttl`() = runTest {
        val provider = provider()
        assertNull(provider.peek())
        provider.current()
        assertNotNull(provider.peek())
        clock.advanceBy(policy.ttl)
        assertNull(provider.peek())
    }

    @Test
    fun `peek on a fallback is null once the backoff allows a new attempt`() = runTest {
        remote.fail()
        val provider = provider()
        provider.current()
        assertNotNull(provider.peek())
        clock.advanceBy(Duration.ofMinutes(1))
        assertNull(provider.peek())
    }

    // Failures that are not a RemoteConfigException

    private val throwingWrites = object : TtcConfigCache by cache {
        val writes = java.util.concurrent.atomic.AtomicInteger()

        override suspend fun writeConfig(config: CachedGatewayConfig) {
            writes.incrementAndGet()
            throw IllegalStateException("disk broke")
        }
    }

    @Test
    fun `a runtime exception from remote on a cold start falls back and backs off`() = runTest {
        remote.behavior = { throw IllegalStateException("boom") }
        val provider = provider()
        repeat(3) { assertEquals(ConfigSource.FALLBACK, provider.current().source) }
        runCurrent()
        assertEquals(1, remote.fetches.get())
        clock.advanceBy(policy.failureBackoffInitial)
        provider.current()
        runCurrent()
        assertEquals(2, remote.fetches.get())
    }

    @Test
    fun `a cancellation exception thrown by remote itself is a failed fetch`() = runTest {
        remote.behavior = { throw kotlinx.coroutines.CancellationException("inner timeout") }
        val provider = provider()
        assertEquals(ConfigSource.FALLBACK, provider.current().source)
        provider.current()
        runCurrent()
        assertEquals(1, remote.fetches.get())
    }

    @Test
    fun `a cache write that throws after a good cold start fetch still activates the key once`() = runTest {
        val provider = DefaultGatewayConfigProvider(throwingWrites, remote, fallback, clock, backgroundScope, policy)
        repeat(5) {
            val config = provider.current()
            assertEquals(REMOTE_KEY, config.apiKey)
            assertEquals(ConfigSource.REMOTE, config.source)
            runCurrent()
        }
        assertEquals(1, remote.fetches.get())
        assertEquals(1, throwingWrites.writes.get())
    }

    @Test
    fun `a cache write that throws in a background refresh still activates the key once`() = runTest {
        seedCache(age = Duration.ofDays(1))
        val provider = DefaultGatewayConfigProvider(throwingWrites, remote, fallback, clock, backgroundScope, policy)
        assertEquals(CACHED_KEY, provider.current().apiKey)
        runCurrent()
        repeat(5) {
            assertEquals(REMOTE_KEY, provider.current().apiKey)
            runCurrent()
        }
        assertEquals(1, remote.fetches.get())
    }

    @Test
    fun `a cache read that throws is a cache miss`() = runTest {
        val unreadable = object : TtcConfigCache by cache {
            override suspend fun readConfig(): CachedGatewayConfig? = throw IllegalStateException("corrupt")
        }
        val config = DefaultGatewayConfigProvider(
            unreadable,
            remote,
            fallback,
            clock,
            backgroundScope,
            policy
        ).current()
        assertEquals(REMOTE_KEY, config.apiKey)
        assertEquals(REMOTE_KEY, cache.readConfig()?.apiKey)
    }

    private companion object {
        const val REMOTE_KEY = "sentinel-remote-key"
        const val CACHED_KEY = "sentinel-cached-key"
        const val FALLBACK_KEY = "sentinel-fallback-key"
        const val FALLBACK_BASE_URL = "https://fallback.example.com/pis-gateway"
    }
}
