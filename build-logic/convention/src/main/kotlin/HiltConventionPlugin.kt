import com.google.devtools.ksp.gradle.KspExtension
import ge.hackerman.gza.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // KSP must be applied before Hilt so Hilt picks KSP instead of kapt.
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            // Validate every module, not only what is injected somewhere: bindings nobody
            // requests yet (the network graph until T03) would otherwise compile unchecked.
            extensions.configure<KspExtension> {
                arg("dagger.fullBindingGraphValidation", "ERROR")
            }

            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
        }
    }
}
