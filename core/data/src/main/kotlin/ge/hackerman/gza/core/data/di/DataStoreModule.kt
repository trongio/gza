package ge.hackerman.gza.core.data.di

import android.content.Context
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
}

/**
 * A corrupt file is replaced by [default] instead of failing every read: for the config that
 * costs one refetch, for preferences it loses them (logged by the repository, never crashes).
 */
internal fun <T> jsonDataStore(
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope,
    produceFile: () -> File
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonDataStoreSerializer(serializer, default),
    corruptionHandler = ReplaceFileCorruptionHandler { default },
    scope = scope,
    produceFile = produceFile
)
