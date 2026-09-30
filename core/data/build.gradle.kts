plugins {
    alias(libs.plugins.gza.android.library)
    alias(libs.plugins.gza.hilt)
    alias(libs.plugins.gza.android.robolectric)
}

android {
    namespace = "ge.hackerman.gza.core.data"
}

dependencies {
    api(projects.core.model)
    // Repositories take the gateway client, and the config cache implements a :core:ttc interface.
    api(projects.core.ttc)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
