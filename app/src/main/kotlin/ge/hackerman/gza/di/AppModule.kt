package ge.hackerman.gza.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.BuildConfig
import ge.hackerman.gza.core.model.TBILISI_ZONE
import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Inject this Clock instead of calling Instant.now(), so tests can freeze time.
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(TBILISI_ZONE)

    // Build-time values from ttc.properties, used only when Remote Config cannot supply the key.
    @Provides
    @Singleton
    fun provideTtcFallbackConfig(): TtcFallbackConfig = TtcFallbackConfig(
        gatewayBaseUrl = BuildConfig.TTC_GATEWAY_BASE_URL,
        gatewayKey = BuildConfig.TTC_GATEWAY_KEY,
        firebaseApiKey = BuildConfig.TTC_FIREBASE_API_KEY,
        firebaseProjectId = BuildConfig.TTC_FIREBASE_PROJECT_ID,
        firebaseAppId = BuildConfig.TTC_FIREBASE_APP_ID
    )
}
