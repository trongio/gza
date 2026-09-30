import ge.hackerman.gza.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Roborazzi screenshot and Compose UI tests on top of [AndroidRobolectricConventionPlugin]. */
class AndroidScreenshotTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("gza.android.robolectric")
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.configure<RoborazziExtension> {
                outputDir.set(file("src/test/screenshots"))
            }

            // Waits for either Android plugin, which creates the test configurations, so this
            // works whatever order the plugins are listed in.
            listOf("com.android.application", "com.android.library").forEach { id ->
                pluginManager.withPlugin(id) { configureScreenshotDependencies() }
            }
        }
    }

    private fun Project.configureScreenshotDependencies() {
        dependencies {
            // ui-test-junit4 is versionless; do not rely on the compose plugin for the BOM.
            add("testImplementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            listOf(
                "roborazzi",
                "roborazzi-compose",
                "roborazzi-junit-rule",
                "androidx-compose-ui-test-junit4",
                "androidx-test-espresso-core"
            ).forEach { add("testImplementation", libs.findLibrary(it).get()) }
        }
    }
}
