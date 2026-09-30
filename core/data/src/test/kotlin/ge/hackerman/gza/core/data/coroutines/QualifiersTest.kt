package ge.hackerman.gza.core.data.coroutines

import javax.inject.Qualifier
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class QualifiersTest {
    // Without @Qualifier, Dagger would treat these as plain annotations and bind the bare type.
    @Test
    fun `every coroutine annotation is a dagger qualifier`() {
        listOf(IoDispatcher::class, DefaultDispatcher::class, ApplicationScope::class).forEach {
            assertTrue(it.java.isAnnotationPresent(Qualifier::class.java), "${it.simpleName} is not a qualifier")
        }
    }
}
