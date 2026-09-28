import com.android.build.api.dsl.ApplicationExtension
import ge.hackerman.gza.buildlogic.readTtcProperties
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Bakes the gateway fallback config from `ttc.properties` into BuildConfig. */
class TtcBuildConfigConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val props = readTtcProperties()
            fun prop(key: String, fallback: String = "") = props[key]?.takeIf { it.isNotEmpty() } ?: fallback

            // Waits for the Android plugin so this works whatever order the plugins are listed in.
            var applied = false
            pluginManager.withPlugin(ANDROID_APPLICATION) {
                applied = true
                extensions.configure<ApplicationExtension> {
                    buildFeatures.buildConfig = true
                    defaultConfig.apply {
                        buildConfigField(
                            "String",
                            "TTC_GATEWAY_BASE_URL",
                            prop("ttc.gatewayBaseUrl", DEFAULT_GATEWAY_BASE_URL).asJavaString()
                        )
                        buildConfigField("String", "TTC_GATEWAY_KEY", prop("ttc.gatewayKey").asJavaString())
                        buildConfigField("String", "TTC_FIREBASE_API_KEY", prop("ttc.firebaseApiKey").asJavaString())
                        buildConfigField(
                            "String",
                            "TTC_FIREBASE_PROJECT_ID",
                            prop("ttc.firebaseProjectId").asJavaString()
                        )
                        buildConfigField("String", "TTC_FIREBASE_APP_ID", prop("ttc.firebaseAppId").asJavaString())
                    }
                }
            }
            // withPlugin alone would make this plugin a silent no-op on any other module.
            afterEvaluate {
                if (!applied) {
                    throw GradleException("gza.ttc.buildconfig requires $ANDROID_APPLICATION")
                }
            }
        }
    }

    private fun String.asJavaString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val ANDROID_APPLICATION = "com.android.application"

        // Same default as tools/fetch-ttc-config.sh.
        const val DEFAULT_GATEWAY_BASE_URL = "https://transit.ttc.com.ge/pis-gateway"
    }
}
