package ge.hackerman.gza.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.BuildConfig
import ge.hackerman.gza.core.data.coroutines.ApplicationScope
import ge.hackerman.gza.core.data.coroutines.IoDispatcher
import ge.hackerman.gza.core.ttc.TtcFallbackConfig
import ge.hackerman.gza.core.ttc.config.DefaultGatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import ge.hackerman.gza.core.ttc.config.InMemoryTtcConfigCache
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import ge.hackerman.gza.core.ttc.firebase.FirebaseEndpoints
import ge.hackerman.gza.core.ttc.firebase.FirebaseRemoteConfigClient
import ge.hackerman.gza.core.ttc.firebase.FirebaseWebCredentials
import ge.hackerman.gza.core.ttc.firebase.RemoteGatewayConfigSource
import ge.hackerman.gza.core.ttc.http.GatewayAuthInterceptor
import ge.hackerman.gza.core.ttc.http.TtcHttpClients
import ge.hackerman.gza.core.ttc.http.TtcHttpLogging
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/** The client for Firebase Installations and Remote Config. Never carries the gateway key. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirebaseHttp

/** The client for the TTC gateway. Build Retrofit on TtcGateway.PLACEHOLDER_BASE_URL with it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GatewayHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private val androidLogger = HttpLoggingInterceptor.Logger { Log.d("GzaHttp", it) }

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    @FirebaseHttp
    fun provideFirebaseOkHttpClient(base: OkHttpClient): OkHttpClient =
        buildFirebaseClient(base, BuildConfig.DEBUG, androidLogger)

    @Provides
    fun provideFirebaseCredentials(fallback: TtcFallbackConfig): FirebaseWebCredentials = fallback.firebaseCredentials()

    // T04 replaces this with a DataStore-backed cache.
    @Provides
    @Singleton
    fun provideTtcConfigCache(): TtcConfigCache = InMemoryTtcConfigCache()

    @Provides
    @Singleton
    fun provideRemoteGatewayConfigSource(
        @FirebaseHttp client: OkHttpClient,
        credentials: FirebaseWebCredentials,
        cache: TtcConfigCache,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): RemoteGatewayConfigSource = FirebaseRemoteConfigClient(
        httpClient = client,
        endpoints = FirebaseEndpoints.Production,
        credentials = credentials,
        cache = cache,
        clock = clock,
        ioDispatcher = ioDispatcher
    )

    // Must be a singleton: its mutex and generation only work if every request shares it.
    @Provides
    @Singleton
    fun provideGatewayConfigProvider(
        cache: TtcConfigCache,
        remote: RemoteGatewayConfigSource,
        fallback: TtcFallbackConfig,
        clock: Clock,
        @ApplicationScope refreshScope: CoroutineScope
    ): GatewayConfigProvider = DefaultGatewayConfigProvider(cache, remote, fallback, clock, refreshScope)

    @Provides
    @Singleton
    fun provideGatewayAuthInterceptor(provider: GatewayConfigProvider): GatewayAuthInterceptor =
        GatewayAuthInterceptor(provider)

    @Provides
    @Singleton
    @GatewayHttp
    fun provideGatewayOkHttpClient(base: OkHttpClient, auth: GatewayAuthInterceptor): OkHttpClient =
        buildGatewayClient(base, auth, BuildConfig.DEBUG, androidLogger)
}

// Plain functions rather than a nullable logger binding, so tests can drive both build types.
// Release builds get no logging interceptor at all, not one at level NONE.

internal fun buildFirebaseClient(
    base: OkHttpClient,
    debug: Boolean,
    logger: HttpLoggingInterceptor.Logger
): OkHttpClient = TtcHttpClients.firebaseClient(base, if (debug) TtcHttpLogging.interceptor(logger) else null)

internal fun buildGatewayClient(
    base: OkHttpClient,
    auth: GatewayAuthInterceptor,
    debug: Boolean,
    logger: HttpLoggingInterceptor.Logger
): OkHttpClient = TtcHttpClients.gatewayClient(base, auth, if (debug) TtcHttpLogging.interceptor(logger) else null)
