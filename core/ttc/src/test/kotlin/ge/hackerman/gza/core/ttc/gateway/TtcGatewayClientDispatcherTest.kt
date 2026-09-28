package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Decoding and mapping must run on the injected dispatcher, not on whatever thread the call
 * resumes on: the stops list is thousands of items, too much for the main thread. The parse
 * dispatcher here only runs when the test says so, so a finished request whose result is not
 * there yet proves the work is waiting on that dispatcher.
 */
class TtcGatewayClientDispatcherTest {
    private val gateway = FixtureGateway()
    private val scheduler = TestCoroutineScheduler()
    private val parse = CountingDispatcher(StandardTestDispatcher(scheduler))

    @AfterEach
    fun tearDown() = gateway.close()

    @Test
    fun `stops are decoded on the injected dispatcher`() = runBlocking {
        gateway.serve("stops/all-en.json")
        val client = gateway.client(parseDispatcher = parse)
        val stops = async(Dispatchers.Default) { client.stops(Language.EN) }

        awaitParseQueued(stops)
        scheduler.advanceUntilIdle()

        assertTrue(stops.await().size > 1_000)
    }

    @Test
    fun `a malformed body fails on the injected dispatcher too`() = runBlocking {
        gateway.respondWith { FixtureGateway.response(200, """{"not":"a list"}""", "application/json") }
        val client = gateway.client(parseDispatcher = parse)
        val board = async(Dispatchers.Default) { runCatching { client.arrivalBoard(StopId("1:970"), Language.EN) } }

        awaitParseQueued(board)
        scheduler.advanceUntilIdle()

        assertTrue(board.await().exceptionOrNull() is TtcGatewayException.Malformed)
    }

    private suspend fun awaitParseQueued(result: Deferred<*>) {
        withTimeout(10.seconds) {
            while (parse.dispatches.get() == 0) delay(5)
        }
        assertEquals(1, gateway.requests.size)
        // The response is in, but nothing has been decoded until the parse dispatcher runs.
        assertFalse(result.isCompleted)
    }

    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }
}
