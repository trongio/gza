package ge.hackerman.gza.core.data.sync

import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

class KeyedMutexTest {
    private val mutex = KeyedMutex<String>()

    @Test
    fun `the same key runs one at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = async {
            mutex.withLock("a") {
                order += "first start"
                gate.await()
                order += "first end"
            }
        }
        yield()
        val second = async { mutex.withLock("a") { order += "second" } }
        yield()
        assertEquals(listOf("first start"), order)
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf("first start", "first end", "second"), order)
    }

    @Test
    fun `different keys run in parallel`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val held = async {
            mutex.withLock("a") {
                gate.await()
                order += "a"
            }
        }
        yield()
        mutex.withLock("b") { order += "b" }
        gate.complete(Unit)
        held.await()
        assertEquals(listOf("b", "a"), order)
    }
}
