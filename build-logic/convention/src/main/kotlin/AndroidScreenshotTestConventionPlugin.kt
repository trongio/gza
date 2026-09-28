import com.android.build.api.dsl.CommonExtension
import ge.hackerman.gza.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

class AndroidScreenshotTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            extensions.getByType<CommonExtension>().testOptions.unitTests.isIncludeAndroidResources = true

            extensions.configure<RoborazziExtension> {
                outputDir.set(file("src/test/screenshots"))
            }

            // Robolectric on SDK 36+ writes FileDescriptor internals through
            // jdk.internal.access, which JDK 17+ hides from unnamed modules.
            tasks.withType<Test>().configureEach {
                jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED"
                )
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
                    "androidx-test-ext-junit",
                    "androidx-test-espresso-core"
                ).forEach { add("testImplementation", libs.findLibrary(it).get()) }
                // Robolectric tests are JUnit 4; the vintage engine runs them on the JUnit Platform.
                add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
            }
        }
    }
}
