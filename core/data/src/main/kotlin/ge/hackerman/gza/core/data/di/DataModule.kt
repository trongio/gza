package ge.hackerman.gza.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.data.datastore.DataStoreTtcConfigCache
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindTtcConfigCache(cache: DataStoreTtcConfigCache): TtcConfigCache
}
