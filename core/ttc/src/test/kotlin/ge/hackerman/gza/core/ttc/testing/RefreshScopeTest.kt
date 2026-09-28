package ge.hackerman.gza.core.ttc.testing

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class RefreshScopeTest {
    @Test
    fun `a refresh that throws fails awaitRefreshes`() {
        val scope = RefreshScope()
        scope.launch { error("refresh broke") }
        val failure = assertFailsWith<AssertionError> { scope.awaitRefreshes() }
        assertIs<IllegalStateException>(failure.cause)
        scope.close()
    }

    @Test
    fun `every refresh that threw is reported, the rest as suppressed`() {
        val scope = RefreshScope()
        scope.launch { error("first") }
        scope.launch { throw IllegalArgumentException("second") }
        val failure = assertFailsWith<AssertionError> { scope.awaitRefreshes() }
        assertEquals(1, checkNotNull(failure.cause).suppressed.size)
        scope.close()
    }

    @Test
    fun `a failure left unawaited is reported by close`() {
        val scope = RefreshScope()
        val child = scope.launch { error("late") }
        runBlocking { child.join() }
        assertFailsWith<AssertionError> { scope.close() }
    }

    @Test
    fun `clean refreshes pass`() {
        val scope = RefreshScope()
        scope.launch { }
        scope.awaitRefreshes()
        scope.close()
    }
}
