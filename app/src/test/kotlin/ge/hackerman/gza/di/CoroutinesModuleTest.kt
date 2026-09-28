package ge.hackerman.gza.di

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CoroutinesModuleTest {
    @Test
    fun `a failing child of the application scope is logged by class name and never crashes`() {
        val logged = CopyOnWriteArrayList<String>()
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + applicationScopeExceptionHandler { logged += it })
        try {
            runBlocking {
                scope.launch { throw IllegalStateException("secret-token") }.join()
                // SupervisorJob: the scope still runs later children.
                var ran = false
                scope.launch { ran = true }.join()
                assertTrue(ran)
            }
            assertEquals(listOf("Uncaught in application scope: java.lang.IllegalStateException"), logged)
            assertFalse(logged.any { "secret-token" in it })
            assertTrue(uncaught.isEmpty(), "reached the uncaught handler: $uncaught")
        } finally {
            scope.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
