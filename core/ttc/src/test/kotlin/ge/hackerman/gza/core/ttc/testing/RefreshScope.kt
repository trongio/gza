package ge.hackerman.gza.core.ttc.testing

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * A real-thread scope for the provider's background refreshes, as the app wires it.
 *
 * Unlike the app's scope, whose handler only logs, an exception escaping a refresh here is
 * recorded and rethrown from [awaitRefreshes] and [close], so a test cannot pass while a
 * refresh is crashing behind it.
 */
class RefreshScope private constructor(private val uncaught: MutableList<Throwable>) :
    CoroutineScope by CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e }
    ) {
    constructor() : this(CopyOnWriteArrayList())

    private val job get() = checkNotNull(coroutineContext[Job])

    /** Waits for every refresh launched so far, then fails if any of them threw. */
    fun awaitRefreshes() {
        val children = job.children.toList()
        runBlocking { withTimeout(TIMEOUT_MS) { children.joinAll() } }
        rethrowUncaught()
    }

    fun close() {
        cancel()
        rethrowUncaught()
    }

    private fun rethrowUncaught() {
        val first = uncaught.firstOrNull() ?: return
        uncaught.drop(1).forEach(first::addSuppressed)
        uncaught.clear()
        throw AssertionError("A background refresh threw", first)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
