package ge.hackerman.gza.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Blocking I/O dispatcher. Injected so tests can swap it; never hardcode Dispatchers.IO in classes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Lives as long as the process, for work no screen owns, such as background config refreshes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    // SupervisorJob: one failed child must not cancel every later one. The handler is a
    // safety net: without it a child's uncaught exception reaches the thread's handler and
    // kills the process. Only the class name is logged; messages can carry URLs or tokens.
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher ioDispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + ioDispatcher + applicationScopeExceptionHandler { Log.e(TAG, it) })

    private const val TAG = "GzaAppScope"
}

/** Reports what escaped an application scope child by class name only. */
internal fun applicationScopeExceptionHandler(log: (String) -> Unit): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, e -> log("Uncaught in application scope: ${e.javaClass.name}") }
