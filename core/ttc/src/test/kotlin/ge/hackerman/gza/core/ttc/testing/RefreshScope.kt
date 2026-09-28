package ge.hackerman.gza.core.ttc.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A real-thread scope for the provider's background refreshes, as the app wires it. */
class RefreshScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO) {
    private val job get() = coroutineContext[kotlinx.coroutines.Job]!!

    /** Waits for every refresh launched so far. */
    fun awaitRefreshes() = runBlocking {
        withTimeout(TIMEOUT_MS) { job.children.toList().joinAll() }
    }

    fun close() = cancel()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
