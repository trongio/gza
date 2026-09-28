package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.http.TtcGateway
import java.time.Clock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object TtcGatewayClientFactory {
    /**
     * [gatewayHttpClient] must be the client from [ge.hackerman.gza.core.ttc.http.TtcHttpClients.gatewayClient]:
     * requests are built on [TtcGateway.PLACEHOLDER_BASE_URL] and only its auth interceptor
     * moves them to the real gateway with the key. Build once and share: Retrofit caches
     * its method parsing per instance.
     */
    fun create(gatewayHttpClient: OkHttpClient, clock: Clock, validateEagerly: Boolean = false): TtcGatewayClient {
        val retrofit = Retrofit.Builder()
            .baseUrl(TtcGateway.PLACEHOLDER_BASE_URL)
            .client(gatewayHttpClient)
            .addConverterFactory(TtcJson.asConverterFactory("application/json".toMediaType()))
            .validateEagerly(validateEagerly)
            .build()
        return RetrofitTtcGatewayClient(retrofit.create(TtcGatewayService::class.java), clock)
    }
}
