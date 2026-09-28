package ge.hackerman.gza.core.ttc.firebase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Request DTOs carry no default values: the lenient Json does not encode defaults, so a
// default would silently vanish from the request body.

@Serializable
internal data class InstallationRequest(
    val fid: String,
    val appId: String,
    val authVersion: String,
    val sdkVersion: String
)

@Serializable
internal data class InstallationResponse(
    val fid: String? = null,
    val refreshToken: String? = null,
    val authToken: AuthTokenDto? = null
)

@Serializable
internal data class AuthTokenDto(val token: String? = null, val expiresIn: String? = null)

@Serializable
internal data class GenerateAuthTokenRequest(val installation: InstallationInfo)

@Serializable
internal data class InstallationInfo(val sdkVersion: String, val appId: String)

@Serializable
internal data class FetchRequest(
    val appInstanceId: String,
    /** Omitted from the body when null (explicitNulls = false). */
    val appInstanceIdToken: String?,
    val appId: String,
    val sdkVersion: String,
    val languageCode: String
)

/** Entries are strings today; [JsonElement] keeps one odd value from failing the whole fetch. */
@Serializable
internal data class FetchResponse(
    val entries: Map<String, JsonElement>? = null,
    val state: String? = null,
    val templateVersion: String? = null
)

@Serializable
internal data class GoogleErrorEnvelope(val error: GoogleError? = null)

@Serializable
internal data class GoogleError(val code: Int? = null, val status: String? = null)
