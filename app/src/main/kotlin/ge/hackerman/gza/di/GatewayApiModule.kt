package ge.hackerman.gza.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClientFactory
import java.time.Clock
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object GatewayApiModule {
    // Singleton so Retrofit builds its proxy and parses each endpoint's annotations once.
    @Provides
    @Singleton
    fun provideTtcGatewayClient(@GatewayHttp client: OkHttpClient, clock: Clock): TtcGatewayClient =
        TtcGatewayClientFactory.create(client, clock)
}
