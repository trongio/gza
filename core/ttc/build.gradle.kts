plugins {
    alias(libs.plugins.gza.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    // OkHttp types (OkHttpClient, Interceptor, HttpUrl) are part of this module's API.
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.okhttp.logging.interceptor)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // Retrofit stays an implementation detail: no Retrofit type is in this module's API.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
