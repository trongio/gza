import ge.hackerman.gza.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A `:feature:*` module: a Compose Android library with screenshot tests, type-safe
 * navigation routes (`@Serializable`) and the design system. Features never depend on
 * each other; `:app` wires them together.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("gza.android.library")
            pluginManager.apply("gza.android.library.compose")
            pluginManager.apply("gza.android.screenshot")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-core").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
