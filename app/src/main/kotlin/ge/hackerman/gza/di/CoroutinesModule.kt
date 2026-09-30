package ge.hackerman.gza.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.data.coroutines.ApplicationScope
import ge.hackerman.gza.core.data.coroutines.DefaultDispatcher
import ge.hackerman.gza.core.data.coroutines.IoDispatcher
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

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
