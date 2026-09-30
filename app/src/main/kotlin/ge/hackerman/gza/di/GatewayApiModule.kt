package ge.hackerman.gza.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.data.coroutines.DefaultDispatcher
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClientFactory
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object GatewayApiModule {
    // Singleton so Retrofit builds its proxy and parses each endpoint's annotations once.
    @Provides
    @Singleton
    fun provideTtcGatewayClient(
        @GatewayHttp client: OkHttpClient,
        clock: Clock,
        @DefaultDispatcher parseDispatcher: CoroutineDispatcher
    ): TtcGatewayClient = TtcGatewayClientFactory.create(client, clock, parseDispatcher)
}
