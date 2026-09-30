package ge.hackerman.gza.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.data.datastore.DataStoreTtcConfigCache
import ge.hackerman.gza.core.data.language.AndroidContentLanguage
import ge.hackerman.gza.core.data.language.ContentLanguage
import ge.hackerman.gza.core.data.repository.DataStoreUserPreferencesRepository
import ge.hackerman.gza.core.data.repository.OfflineFirstRouteRepository
import ge.hackerman.gza.core.data.repository.OfflineFirstStopRepository
import ge.hackerman.gza.core.data.repository.RouteRepository
import ge.hackerman.gza.core.data.repository.StopRepository
import ge.hackerman.gza.core.data.repository.UserPreferencesRepository
import ge.hackerman.gza.core.data.sync.StalenessPolicy
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindTtcConfigCache(cache: DataStoreTtcConfigCache): TtcConfigCache

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        repository: DataStoreUserPreferencesRepository
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindStopRepository(repository: OfflineFirstStopRepository): StopRepository

    @Binds
    @Singleton
    abstract fun bindRouteRepository(repository: OfflineFirstRouteRepository): RouteRepository

    @Binds
    @Singleton
    abstract fun bindContentLanguage(language: AndroidContentLanguage): ContentLanguage

    companion object {
        @Provides
        fun provideStalenessPolicy(): StalenessPolicy = StalenessPolicy()
    }
}
