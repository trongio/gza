package ge.hackerman.gza.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

/**
 * Pure JVM modules compile against the build's JDK but run on Android's library, so a JDK 9+
 * call (say `InputStream.readNBytes`, API 33) compiles, passes every JVM test and then throws
 * NoSuchMethodError on an older phone. Android lint does not check these modules for that, so
 * Animal Sniffer checks the main classes against the Android API [MIN_SDK] signature. Tests
 * only ever run on the JVM and are not checked.
 */
internal fun Project.configureAndroidApiCheck() {
    pluginManager.apply("ru.vyarus.animalsniffer")

    val signature = libs.lib("gummy-bears-api26").get()
    check(signature.name.endsWith("-$MIN_SDK")) { "API signature ${signature.name} does not match minSdk $MIN_SDK" }
    dependencies.add("signature", "${signature.module}:${signature.versionConstraint.requiredVersion}@signature")

    extensions.configure<AnimalSnifferExtension> {
        sourceSets = listOf(extensions.getByType<SourceSetContainer>().getByName("main"))
    }

    // `lint` is in the local gate and CI, and `check` is what Gradle users reach for: both run
    // the API check, so neither route can skip it.
    listOf("lint", "check").forEach { name -> tasks.named(name).configure { dependsOn("animalsnifferMain") } }
}
