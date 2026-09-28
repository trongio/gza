import com.android.build.api.dsl.Lint
import ge.hackerman.gza.buildlogic.configureAndroidApiCheck
import ge.hackerman.gza.buildlogic.configureDetekt
import ge.hackerman.gza.buildlogic.configureJUnitPlatform
import ge.hackerman.gza.buildlogic.configureKotlinJvm
import ge.hackerman.gza.buildlogic.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")

            configureKotlinJvm()
            extensions.configure<Lint> {
                configureLint()
            }
            configureDetekt()
            configureJUnitPlatform()
            configureAndroidApiCheck()
        }
    }
}
