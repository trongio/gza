import com.android.build.api.dsl.LibraryExtension
import ge.hackerman.gza.buildlogic.TARGET_SDK
import ge.hackerman.gza.buildlogic.configureDetekt
import ge.hackerman.gza.buildlogic.configureJUnitPlatform
import ge.hackerman.gza.buildlogic.configureKotlinAndroid
import ge.hackerman.gza.buildlogic.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                lint.targetSdk = TARGET_SDK
                testOptions.targetSdk = TARGET_SDK
                lint.configureLint()
            }
            configureDetekt()
            configureJUnitPlatform()
        }
    }
}
