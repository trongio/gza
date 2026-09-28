package ge.hackerman.gza.core.ttc.firebase

/** The Firebase web config TTC embeds in its page, baked into the build from `ttc.properties`. */
class FirebaseWebCredentials(val apiKey: String, val projectId: String, val appId: String) {
    val isComplete: Boolean get() = apiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank()

    override fun equals(other: Any?): Boolean = other is FirebaseWebCredentials &&
        apiKey == other.apiKey &&
        projectId == other.projectId &&
        appId == other.appId

    override fun hashCode(): Int = listOf(apiKey, projectId, appId).hashCode()

    override fun toString(): String =
        "FirebaseWebCredentials(apiKey=${redact(apiKey)}, projectId=$projectId, appId=${redact(appId)})"

    private fun redact(secret: String): String = if (secret.isBlank()) "<empty>" else "<redacted>"
}
