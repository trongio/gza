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
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ttc)
    implementation(projects.core.model)
    implementation(projects.feature.now)
    implementation(projects.feature.search)
    implementation(projects.feature.map)
    implementation(projects.feature.plan)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.navigation.testing)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
