package ge.hackerman.gza.core.ttc.firebase

import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import ge.hackerman.gza.core.ttc.config.GatewayKeys
import ge.hackerman.gza.core.ttc.config.TtcConfigCache
import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException.Reason
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * Fetches `PIS_GATEWAY_KEY` and `PIS_GATEWAY_BASE_URL` the way the TTC web app does:
 * register (or reuse) a Firebase installation, then call the Remote Config fetch REST API.
 * See docs/TTC_API.md, "Remote Config (runtime key)".
 *
 * [httpClient] must be the Firebase client, never the gateway one.
 */
class FirebaseRemoteConfigClient(
    httpClient: OkHttpClient,
    private val endpoints: FirebaseEndpoints,
    private val credentials: FirebaseWebCredentials,
    private val cache: TtcConfigCache,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    json: Json = TtcJson,
    private val newFid: () -> String = { FirebaseInstallationIds.generate() }
) : RemoteGatewayConfigSource {
    private val rest = FirebaseRest(httpClient, json, credentials.apiKey)

    override suspend fun fetch(): RemoteGatewayConfig = withContext(ioDispatcher) {
        if (!credentials.isComplete) throw RemoteConfigException(Reason.NOT_CONFIGURED)
        fetchConfig(installationOrNull())
    }

    // Installations failing is never fatal: Remote Config does not enforce the token today,
    // so the fetch still runs without one.
    private suspend fun installationOrNull(): FirebaseInstallation? {
        val now = clock.instant()
        val stored = cache.readInstallation()
        return when {
            stored == null -> register()

            stored.authTokenExpiresAt > now + TOKEN_REFRESH_MARGIN -> stored

            else -> when (val refreshed = refreshToken(stored)) {
                is TokenRefresh.Refreshed -> refreshed.installation

                TokenRefresh.Rejected -> {
                    cache.writeInstallation(null)
                    register()
                }

                TokenRefresh.Failed -> stored.takeIf { it.authTokenExpiresAt > now }
            }
        }
    }

    private suspend fun register(): FirebaseInstallation? {
        val request = InstallationRequest(
            fid = newFid(),
            appId = credentials.appId,
            authVersion = AUTH_VERSION,
            sdkVersion = INSTALLATIONS_SDK_VERSION
        )
        val installation = try {
            val result = rest.post(installationsUrl(), InstallationRequest.serializer(), request)
            if (result.isSuccessful) parseInstallation(result.body) else null
        } catch (ignored: RemoteConfigException) {
            // Deliberate: see installationOrNull().
            null
        }
        installation?.let { cache.writeInstallation(it) }
        return installation
    }

    // Uses the returned fid: the server replaces a malformed one with its own.
    private fun parseInstallation(body: String): FirebaseInstallation? {
        val response = rest.decode(InstallationResponse.serializer(), body)
        val fid = response.fid
        val refreshToken = response.refreshToken
        val authToken = response.authToken?.token
        return if (fid.isNullOrBlank() || refreshToken.isNullOrBlank() || authToken.isNullOrBlank()) {
            null
        } else {
            FirebaseInstallation(
                fid = fid,
                refreshToken = refreshToken,
                authToken = authToken,
                authTokenExpiresAt = clock.instant() + parseExpiresIn(response.authToken.expiresIn)
            )
        }
    }

    private suspend fun refreshToken(stored: FirebaseInstallation): TokenRefresh {
        val url = installationsUrl().newBuilder()
            .addPathSegment(stored.fid)
            .addPathSegment("authTokens:generate")
            .build()
        val request = GenerateAuthTokenRequest(InstallationInfo(INSTALLATIONS_SDK_VERSION, credentials.appId))
        val outcome = try {
            val result = rest.post(
                url,
                GenerateAuthTokenRequest.serializer(),
                request,
                authorization = "$AUTH_VERSION ${stored.refreshToken}"
            )
            when {
                result.code == HTTP_UNAUTHORIZED || result.code == HTTP_NOT_FOUND -> TokenRefresh.Rejected
                !result.isSuccessful -> TokenRefresh.Failed
                else -> rest.decode(AuthTokenDto.serializer(), result.body).toRefresh(stored)
            }
        } catch (ignored: RemoteConfigException) {
            // Deliberate: a failed refresh falls back to the stored token while it lasts.
            TokenRefresh.Failed
        }
        if (outcome is TokenRefresh.Refreshed) cache.writeInstallation(outcome.installation)
        return outcome
    }

    private fun AuthTokenDto.toRefresh(stored: FirebaseInstallation): TokenRefresh {
        val value = token
        return if (value.isNullOrBlank()) {
            TokenRefresh.Failed
        } else {
            TokenRefresh.Refreshed(
                stored.copy(authToken = value, authTokenExpiresAt = clock.instant() + parseExpiresIn(expiresIn))
            )
        }
    }

    private fun fetchConfig(installation: FirebaseInstallation?): RemoteGatewayConfig {
        val request = FetchRequest(
            appInstanceId = installation?.fid ?: newFid(),
            appInstanceIdToken = installation?.authToken,
            appId = credentials.appId,
            sdkVersion = REMOTE_CONFIG_SDK_VERSION,
            languageCode = LANGUAGE_CODE
        )
        val url = endpoints.remoteConfigBaseUrl.newBuilder()
            .addPathSegment("v1")
            .addPathSegment("projects")
            .addPathSegment(credentials.projectId)
            .addPathSegment("namespaces")
            .addPathSegment("firebase:fetch")
            .build()
        val result = rest.post(url, FetchRequest.serializer(), request)
        if (!result.isSuccessful) {
            throw RemoteConfigException(
                Reason.HTTP,
                httpCode = result.code,
                googleStatus = rest.googleStatus(result.body)
            )
        }
        val entries = rest.decode(FetchResponse.serializer(), result.body).entries.orEmpty()
        val apiKey = entries.string(KEY_ENTRY)?.trim().orEmpty()
        val failure = when {
            apiKey.isEmpty() -> Reason.MISSING_KEY
            !GatewayKeys.isUsableApiKey(apiKey) -> Reason.MALFORMED
            else -> null
        }
        failure?.let { throw RemoteConfigException(it) }
        return RemoteGatewayConfig(baseUrl = entries.string(BASE_URL_ENTRY), apiKey = apiKey)
    }

    private fun installationsUrl(): HttpUrl = endpoints.installationsBaseUrl.newBuilder()
        .addPathSegment("v1")
        .addPathSegment("projects")
        .addPathSegment(credentials.projectId)
        .addPathSegment("installations")
        .build()

    private fun Map<String, JsonElement>.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private sealed interface TokenRefresh {
        class Refreshed(val installation: FirebaseInstallation) : TokenRefresh
        data object Rejected : TokenRefresh
        data object Failed : TokenRefresh
    }

    companion object {
        const val KEY_ENTRY: String = "PIS_GATEWAY_KEY"
        const val BASE_URL_ENTRY: String = "PIS_GATEWAY_BASE_URL"

        private const val AUTH_VERSION = "FIS_v2"
        private const val INSTALLATIONS_SDK_VERSION = "w:0.6.4"
        private const val REMOTE_CONFIG_SDK_VERSION = "0.4.0"
        private const val LANGUAGE_CODE = "en-US"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val MILLIS_PER_SECOND = 1_000
        private val TOKEN_REFRESH_MARGIN: Duration = Duration.ofHours(1)

        // Caps absurd values so they cannot overflow; Firebase itself sends 7 days.
        private val MAX_EXPIRY: Duration = Duration.ofDays(365)

        /** `"604800s"` or `"3.5s"`; anything unparseable counts as already expired. */
        internal fun parseExpiresIn(raw: String?): Duration {
            val seconds = raw?.trim()?.removeSuffix("s")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                ?: return Duration.ZERO
            val millis = seconds.multiply(BigDecimal(MILLIS_PER_SECOND)).min(BigDecimal(MAX_EXPIRY.toMillis()))
            return Duration.ofMillis(millis.toLong())
        }
    }
}
