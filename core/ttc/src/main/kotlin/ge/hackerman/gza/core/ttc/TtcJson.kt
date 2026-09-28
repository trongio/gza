package ge.hackerman.gza.core.ttc

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for everything TTC and Firebase send: unknown keys are ignored, and a null
 * where a default exists falls back to the default instead of failing the whole response.
 */
val TtcJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
