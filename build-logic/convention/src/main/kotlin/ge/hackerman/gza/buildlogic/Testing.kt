package ge.hackerman.gza.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

internal fun Project.configureJUnitPlatform() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("kotlin-test"))
        // Gradle 9 no longer injects a launcher.
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
}
