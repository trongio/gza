plugins {
    alias(libs.plugins.gza.android.library)
    alias(libs.plugins.gza.android.library.compose)
    alias(libs.plugins.gza.android.screenshot)
}

android {
    namespace = "ge.hackerman.gza.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(projects.core.testing)
}
