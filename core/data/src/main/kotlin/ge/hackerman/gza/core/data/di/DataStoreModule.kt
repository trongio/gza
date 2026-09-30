package ge.hackerman.gza.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.data.coroutines.IoDispatcher
import ge.hackerman.gza.core.data.datastore.DataStoreFiles
import ge.hackerman.gza.core.data.datastore.JsonDataStoreSerializer
import ge.hackerman.gza.core.data.datastore.TtcConfigData
import ge.hackerman.gza.core.data.datastore.UserPreferencesData
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer

// Singletons: two DataStores on one file throw, so each file gets exactly one instance.
@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun provideTtcConfigDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DataStore<TtcConfigData> = jsonDataStore(
        TtcConfigData.serializer(),
        TtcConfigData(),
        CoroutineScope(ioDispatcher + SupervisorJob())
    ) { context.dataStoreFile(DataStoreFiles.TTC_CONFIG) }

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DataStore<UserPreferencesData> = jsonDataStore(
        UserPreferencesData.serializer(),
        UserPreferencesData(),
        CoroutineScope(ioDispatcher + SupervisorJob()),
        onCorruption = { Log.w(TAG, "User preferences unreadable, reset: ${it.javaClass.name}") }
    ) { context.dataStoreFile(DataStoreFiles.USER_PREFERENCES) }

    private const val TAG = "GzaData"
}

/**
 * A corrupt file is replaced by [default] instead of failing every read: for the config that
 * costs one refetch, for preferences it loses them, which [onCorruption] logs, by class name
 * only.
 */
internal fun <T> jsonDataStore(
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope,
    onCorruption: (CorruptionException) -> Unit = {},
    produceFile: () -> File
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonDataStoreSerializer(serializer, default),
    corruptionHandler = ReplaceFileCorruptionHandler {
        onCorruption(it)
        default
    },
    scope = scope,
    produceFile = produceFile
)
