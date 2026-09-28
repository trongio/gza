package ge.hackerman.gza.core.ttc.testing

import ge.hackerman.gza.core.ttc.firebase.FirebaseWebCredentials
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.MockResponse

/** Sentinel values only: nothing here is, or looks like, a real key. */
object FirebaseFixtures {
    const val FIREBASE_KEY = "sentinel-firebase-key"
    const val PROJECT_ID = "test-project"
    const val APP_ID = "1:0:web:sentinel"
    const val GATEWAY_KEY = "sentinel-gateway-key-0001"
    const val FID = "cSentinelFid0000000001"
    const val REFRESH_TOKEN = "sentinel-refresh-token-0001"
    const val AUTH_TOKEN = "sentinel-auth-token-0001"
    const val AUTH_TOKEN_2 = "sentinel-auth-token-0002"
    const val PRODUCTION_BASE_URL = "https://transit.ttc.com.ge/pis-gateway"

    /** Every secret a log line or message must never contain. */
    val SECRETS = listOf(FIREBASE_KEY, GATEWAY_KEY, REFRESH_TOKEN, AUTH_TOKEN, AUTH_TOKEN_2)

    val credentials = FirebaseWebCredentials(FIREBASE_KEY, PROJECT_ID, APP_ID)

    fun read(name: String): String = checkNotNull(javaClass.getResource("/firebase/$name")) { "missing fixture $name" }
        .readText()

    /** A fetch response serving [key], with the other entries a real template also has. */
    fun fetchOk(key: String = GATEWAY_KEY, baseUrl: String? = PRODUCTION_BASE_URL): String = buildJsonObject {
        putJsonObject("entries") {
            baseUrl?.let { put("PIS_GATEWAY_BASE_URL", it) }
            put("PIS_GATEWAY_KEY", key)
            put("OTS_GATEWAY_BASE_URL", "https://example.invalid/ots")
        }
        put("state", "UPDATE")
        put("templateVersion", "33")
    }.toString()

    fun json(code: Int, body: String, headersDelayMs: Long = 0): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json; charset=UTF-8")
        .body(body)
        .apply { if (headersDelayMs > 0) headersDelay(headersDelayMs, TimeUnit.MILLISECONDS) }
        .build()
}
