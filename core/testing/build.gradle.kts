plugins {
    alias(libs.plugins.gza.android.library)
    alias(libs.plugins.gza.android.library.compose)
}

android {
    namespace = "ge.hackerman.gza.core.testing"
}

dependencies {
    implementation(projects.core.designsystem)

    api(libs.androidx.compose.ui.test.junit4)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.robolectric)
    api(libs.junit4)
    api(libs.androidx.test.ext.junit)
    // Compose ui-test still pulls espresso 3.5.1, which crashes on SDK 36+ (InputManager.getInstance()).
    api(libs.androidx.test.espresso.core)
}
