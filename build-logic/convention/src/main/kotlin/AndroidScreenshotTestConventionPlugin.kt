import com.android.build.api.dsl.CommonExtension
import ge.hackerman.gza.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidScreenshotTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.getByType<CommonExtension>().testOptions.unitTests.isIncludeAndroidResources = true

            extensions.configure<RoborazziExtension> {
                outputDir.set(file("src/test/screenshots"))
            }

            dependencies {
                listOf(
                    "robolectric",
                    "roborazzi",
                    "roborazzi-compose",
                    "roborazzi-junit-rule",
                    "androidx-compose-ui-test-junit4",
                    "junit4",
                    "androidx-test-core",
                    "androidx-test-ext-junit"
                ).forEach { add("testImplementation", libs.findLibrary(it).get()) }
                // Robolectric tests are JUnit 4; the vintage engine runs them on the JUnit Platform.
                add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
            }
        }
    }
}
