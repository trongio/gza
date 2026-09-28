package ge.hackerman.gza.core.ttc.http

import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Application interceptor for the gateway client. Moves requests built on
 * [TtcGateway.PLACEHOLDER_BASE_URL] onto the configured base URL and adds `x-api-key`.
 * On a 401 or 403 it asks the provider for rotated credentials once and retries exactly
 * once: the retry is a straight second `proceed`, never a loop.
 *
 * Requests to any other host pass through untouched and without the key, so the key can
 * only ever reach the configured gateway.
 *
 * `runBlocking` is safe here: interceptors run on OkHttp's threads (Retrofit suspend calls
 * enqueue), and the fast path [GatewayConfigProvider.peek] avoids it on almost every call.
 */
class GatewayAuthInterceptor(private val provider: GatewayConfigProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != TtcGateway.PLACEHOLDER_HOST) return chain.proceed(request)

        // Throws GatewayConfigUnavailableException, an IOException, so the call fails cleanly.
        val config = provider.peek() ?: runBlocking { provider.current() }
        val first = chain.proceed(request.authorizedWith(config))
        val retryWith = if (isRejection(first) && request.body?.isOneShot() != true) {
            refreshOrClose(first, config)
        } else {
            null
        }
        return if (retryWith == null) {
            first
        } else {
            first.close()
            chain.proceed(request.authorizedWith(retryWith))
        }
    }

    // The caller never sees [rejected] if the refresh throws, so nobody else would close it
    // and its connection would stay pinned.
    @Suppress("TooGenericExceptionCaught") // Closes on any failure, then rethrows it unchanged.
    private fun refreshOrClose(rejected: Response, config: GatewayConfig): GatewayConfig? = try {
        runBlocking { provider.refreshAfterRejection(config) }
    } catch (e: Throwable) {
        rejected.close()
        throw e
    }

    private fun isRejection(response: Response): Boolean =
        response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN

    private fun Request.authorizedWith(config: GatewayConfig): Request = newBuilder()
        .url(GatewayUrls.resolve(url, config.baseUrl))
        .header(TtcGateway.API_KEY_HEADER, config.apiKey)
        .build()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
