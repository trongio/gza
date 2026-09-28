package ge.hackerman.gza.core.ttc.firebase

import ge.hackerman.gza.core.ttc.firebase.RemoteConfigException.Reason
import java.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Blocking JSON POSTs to Google REST APIs, with every failure mapped to [RemoteConfigException]. */
internal class FirebaseRest(private val httpClient: OkHttpClient, private val json: Json, private val apiKey: String) {
    class Result(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in HTTP_OK_RANGE
    }

    /** The API key goes in a header, never the `?key=` query, so it stays out of URLs and URL logs. */
    fun <T> post(url: HttpUrl, serializer: SerializationStrategy<T>, body: T, authorization: String? = null): Result {
        val request = Request.Builder()
            .url(url)
            .header(GOOG_API_KEY_HEADER, apiKey)
            .header("Accept", JSON_MEDIA_TYPE)
            .apply { authorization?.let { header(AUTHORIZATION_HEADER, it) } }
            .post(json.encodeToString(serializer, body).toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        return try {
            httpClient.newCall(request).execute().use { Result(it.code, it.body.string()) }
        } catch (e: IOException) {
            throw RemoteConfigException(Reason.NETWORK, cause = e)
        }
    }

    fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T = try {
        json.decodeFromString(deserializer, body)
    } catch (e: SerializationException) {
        throw RemoteConfigException(Reason.MALFORMED, cause = e)
    } catch (e: IllegalArgumentException) {
        throw RemoteConfigException(Reason.MALFORMED, cause = e)
    }

    /** `status` from the Google error envelope, or null when the body is not one. */
    fun googleStatus(body: String): String? = try {
        json.decodeFromString(GoogleErrorEnvelope.serializer(), body).error?.status
    } catch (ignored: SerializationException) {
        null
    } catch (ignored: IllegalArgumentException) {
        null
    }

    companion object {
        const val GOOG_API_KEY_HEADER: String = "x-goog-api-key"
        const val AUTHORIZATION_HEADER: String = "Authorization"
        private const val JSON_MEDIA_TYPE = "application/json"
        private val HTTP_OK_RANGE = 200..299
    }
}
