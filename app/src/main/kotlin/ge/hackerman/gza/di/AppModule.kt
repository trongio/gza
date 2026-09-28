package ge.hackerman.gza.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.model.TBILISI_ZONE
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Inject this Clock instead of calling Instant.now(), so tests can freeze time.
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(TBILISI_ZONE)
}
