plugins {
    alias(libs.plugins.gza.android.application)
    alias(libs.plugins.gza.android.application.compose)
    alias(libs.plugins.gza.hilt)
    alias(libs.plugins.gza.android.screenshot)
    alias(libs.plugins.gza.ttc.buildconfig)
}

android {
    namespace = "ge.hackerman.gza"

    defaultConfig {
        applicationId = "ge.hackerman.gza"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        // Per-app language (en/ka) from system settings.
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ttc)
    implementation(projects.core.model)
    implementation(projects.feature.now)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
