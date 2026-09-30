plugins {
    alias(libs.plugins.gza.android.library)
    alias(libs.plugins.gza.hilt)
    alias(libs.plugins.gza.android.robolectric)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room3)
}

android {
    namespace = "ge.hackerman.gza.core.data"
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        // The gateway fixtures recorded for :core:ttc, read in place (not copied).
        variant.hostTests.values.forEach { hostTest ->
            hostTest.sources.resources?.addStaticSourceDirectory("../ttc/src/test/resources")
            hostTest.sources.assets?.addStaticSourceDirectory("schemas")
        }
    }
}

dependencies {
    api(projects.core.model)
    // Repositories take the gateway client, and the config cache implements a :core:ttc interface.
    api(projects.core.ttc)
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.androidx.room3.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
