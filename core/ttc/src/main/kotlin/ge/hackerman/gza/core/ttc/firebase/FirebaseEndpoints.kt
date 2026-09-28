package ge.hackerman.gza.core.ttc.firebase

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Roots of the two Firebase REST APIs. Tests point both at MockWebServer. */
class FirebaseEndpoints(val installationsBaseUrl: HttpUrl, val remoteConfigBaseUrl: HttpUrl) {
    companion object {
        val Production: FirebaseEndpoints = FirebaseEndpoints(
            installationsBaseUrl = "https://firebaseinstallations.googleapis.com/".toHttpUrl(),
            remoteConfigBaseUrl = "https://firebaseremoteconfig.googleapis.com/".toHttpUrl()
        )
    }
}
