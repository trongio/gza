package ge.hackerman.gza.core.ttc.config

import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfig
import ge.hackerman.gza.core.ttc.testing.FakeRemoteGatewayConfigSource
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures.PRODUCTION_BASE_URL
import ge.hackerman.gza.core.ttc.testing.MutableClock
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * The stale while revalidate refresh against the things that can go wrong around it: an
 * unexpected exception inside it, a 401 refetch racing it, and a dead refresh scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGatewayConfigProviderBackgroundRefreshTest {
    private val cache = InMemoryTtcConfigCache()
    private val remote = FakeRemoteGatewayConfigSource().apply { serve(REMOTE_KEY) }
    private val clock = MutableClock()
    private val fallback = TtcFallbackConfig(
        gatewayBaseUrl = FALLBACK_BASE_URL,
        gatewayKey = FALLBACK_KEY,
        firebaseApiKey = "sentinel-firebase-key",
        firebaseProjectId = "test-project",
        firebaseAppId = "1:0:web:sentinel"
    )

    private fun provider(scope: CoroutineScope) = DefaultGatewayConfigProvider(cache, remote, fallback, clock, scope)

    private fun seedStaleCache(key: String = CACHED_KEY) = runBlocking {
        cache.writeConfig(CachedGatewayConfig(PRODUCTION_BASE_URL, key, clock.now - Duration.ofDays(1)))
    }

    // Reads the job outside runBlocking: inside it, coroutineContext is runBlocking's own.
    private fun CoroutineScope.awaitChildren() {
        val job = checkNotNull(coroutineContext[Job])
        runBlocking { withTimeout(TIMEOUT_MS) { job.children.toList().joinAll() } }
    }

    // A non-IO exception in the background refresh

    @Test
    fun `a runtime exception in a background refresh keeps the stale config and later refreshes still run`() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e }
        )
        try {
            seedStaleCache()
            remote.behavior = { throw IllegalStateException("boom") }
            val provider = provider(scope)
            assertEquals(CACHED_KEY, runBlocking { provider.current() }.apiKey)
            scope.awaitChildren()
            assertEquals(1, remote.fetches.get())

            // The in-flight flag was reset in finally, so the next refresh is not blocked
            // forever once the failure backoff has run out.
            remote.serve(REMOTE_KEY)
            clock.advanceBy(GatewayConfigPolicy().failureBackoffMax)
            assertEquals(CACHED_KEY, runBlocking { provider.current() }.apiKey)
            scope.awaitChildren()
            assertEquals(2, remote.fetches.get())
            assertEquals(REMOTE_KEY, runBlocking { provider.current() }.apiKey)
        } finally {
            scope.cancel()
        }
    }

    @Disabled(
        "Bug: DefaultGatewayConfigProvider.revalidateInBackground only catches IOException. " +
            "A RuntimeException from the refresh (remote source, cache) escapes the launched job, " +
            "and the @ApplicationScope scope has no CoroutineExceptionHandler, so it reaches the " +
            "thread's uncaught exception handler: on Android that kills the process."
    )
    @Test
    fun `a runtime exception in a background refresh never reaches the uncaught exception handler`() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        // Exactly the app's scope: CoroutinesModule.provideApplicationScope.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            seedStaleCache()
            remote.behavior = { throw IllegalStateException("boom") }
            val provider = provider(scope)
            runBlocking { provider.current() }
            scope.awaitChildren()
            assertEquals(1, remote.fetches.get())
            assertTrue(uncaught.isEmpty(), "reached the uncaught handler: $uncaught")
        } finally {
            scope.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun `a runtime exception in a background refresh backs off like any other failure`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
        try {
            seedStaleCache()
            remote.behavior = { throw IllegalStateException("boom") }
            val provider = provider(scope)
            repeat(5) {
                runBlocking { provider.current() }
                scope.awaitChildren()
            }
            assertEquals(1, remote.fetches.get())
        } finally {
            scope.cancel()
        }
    }

    // A 401 refetch racing the background refresh

    @Test
    fun `background refresh returning the same key does not stop a waiting 401 from refetching`() = runTest {
        seedStaleCache()
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        remote.behavior = {
            if (calls.incrementAndGet() == 1) {
                gate.await()
                RemoteGatewayConfig(PRODUCTION_BASE_URL, CACHED_KEY)
            } else {
                RemoteGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY)
            }
        }
        val provider = provider(backgroundScope)
        val stale = provider.current()
        runCurrent()
        val retry = async { provider.refreshAfterRejection(stale) }
        runCurrent()
        assertTrue(retry.isActive)
        gate.complete(Unit)
        assertEquals(REMOTE_KEY, retry.await()?.apiKey)
        assertEquals(2, remote.fetches.get())
        assertEquals(REMOTE_KEY, provider.current().apiKey)
        assertEquals(REMOTE_KEY, cache.readConfig()?.apiKey)
    }

    @Test
    fun `background refresh and 401 both seeing the same key give no retry after two fetches`() = runTest {
        seedStaleCache()
        val gate = CompletableDeferred<Unit>()
        remote.behavior = {
            gate.await()
            RemoteGatewayConfig(PRODUCTION_BASE_URL, CACHED_KEY)
        }
        val provider = provider(backgroundScope)
        val stale = provider.current()
        runCurrent()
        val retry = async { provider.refreshAfterRejection(stale) }
        runCurrent()
        gate.complete(Unit)
        assertNull(retry.await())
        assertEquals(2, remote.fetches.get())
        assertEquals(CACHED_KEY, provider.current().apiKey)
    }

    @Test
    fun `failed background refresh does not stop a waiting 401 from refetching despite the backoff`() = runTest {
        seedStaleCache()
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        remote.behavior = {
            if (calls.incrementAndGet() == 1) {
                gate.await()
                throw RemoteConfigException(RemoteConfigException.Reason.NETWORK)
            }
            RemoteGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY)
        }
        val provider = provider(backgroundScope)
        val stale = provider.current()
        runCurrent()
        val retry = async { provider.refreshAfterRejection(stale) }
        runCurrent()
        gate.complete(Unit)
        assertEquals(REMOTE_KEY, retry.await()?.apiKey)
        assertEquals(2, remote.fetches.get())
        assertEquals(REMOTE_KEY, provider.current().apiKey)
    }

    @Test
    fun `a 401 refetch that lands first makes the queued background refresh a no op`() = runTest {
        seedStaleCache()
        val provider = provider(backgroundScope)
        // Queues the background refresh on the test scheduler without running it.
        val stale = provider.current()
        assertEquals(REMOTE_KEY, provider.refreshAfterRejection(stale)?.apiKey)
        runCurrent()
        assertEquals(1, remote.fetches.get())
        assertEquals(REMOTE_KEY, provider.current().apiKey)
    }

    @Test
    fun `parallel stale reads and 401s on real threads fetch once and end on the rotated key`() {
        repeat(STRESS_ROUNDS) { round ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val roundCache = InMemoryTtcConfigCache()
            val roundRemote = FakeRemoteGatewayConfigSource()
            roundRemote.behavior = {
                delay(REMOTE_DELAY_MS)
                RemoteGatewayConfig(PRODUCTION_BASE_URL, REMOTE_KEY)
            }
            runBlocking {
                roundCache.writeConfig(
                    CachedGatewayConfig(
                        PRODUCTION_BASE_URL,
                        CACHED_KEY,
                        clock.now - Duration.ofDays(1)
                    )
                )
            }
            val provider = DefaultGatewayConfigProvider(roundCache, roundRemote, fallback, clock, scope)
            val pool = Executors.newFixedThreadPool(THREADS)
            val start = CountDownLatch(1)
            val finalKeys = CopyOnWriteArrayList<String?>()
            try {
                val futures = List(THREADS) {
                    pool.submit {
                        start.await()
                        runBlocking {
                            val config = provider.current()
                            // The gateway only accepts the rotated key.
                            val used = if (config.apiKey ==
                                REMOTE_KEY
                            ) {
                                config
                            } else {
                                provider.refreshAfterRejection(config)
                            }
                            finalKeys += used?.apiKey
                        }
                    }
                }
                start.countDown()
                futures.forEach { it.get(TIMEOUT_MS, TimeUnit.MILLISECONDS) }
                scope.awaitChildren()
                assertEquals(1, roundRemote.fetches.get(), "round $round")
                assertTrue(finalKeys.all { it == REMOTE_KEY }, "round $round: $finalKeys")
                assertEquals(REMOTE_KEY, runBlocking { provider.current() }.apiKey)
                assertEquals(REMOTE_KEY, runBlocking { roundCache.readConfig() }?.apiKey)
            } finally {
                pool.shutdownNow()
                scope.cancel()
            }
        }
    }

    // A dead refresh scope

    @Test
    fun `a cancelled refresh scope still serves the stale config and never throws`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.cancel()
        seedStaleCache()
        val provider = provider(scope)
        // ATOMIC start still runs the refresh body once on a cancelled scope; either key is fine.
        repeat(3) { assertTrue(runBlocking { provider.current() }.apiKey in setOf(CACHED_KEY, REMOTE_KEY)) }
        assertTrue(remote.fetches.get() <= 1)
    }

    private companion object {
        const val REMOTE_KEY = "sentinel-remote-key"
        const val CACHED_KEY = "sentinel-cached-key"
        const val FALLBACK_KEY = "sentinel-fallback-key"
        const val FALLBACK_BASE_URL = "https://fallback.example.com/pis-gateway"
        const val TIMEOUT_MS = 10_000L
        const val THREADS = 32
        const val STRESS_ROUNDS = 20
        const val REMOTE_DELAY_MS = 30L
    }
}
