package ge.hackerman.gza.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
        // Explicit, because detekt 1.23 does not discover sources from AGP built-in Kotlin.
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
    }

    // detekt 1.23 is compiled against an older Kotlin and crashes when the build's newer
    // Kotlin leaks onto its classpath.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(getSupportedKotlinVersion())
            }
        }
    }
}
