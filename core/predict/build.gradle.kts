plugins {
    alias(libs.plugins.gza.jvm.library)
}

// The real gateway captures live in :core:ttc; read them from there instead of copying.
sourceSets.named("test") {
    resources.srcDir(layout.projectDirectory.dir("../ttc/src/test/resources"))
}

dependencies {
    api(projects.core.model)

    // Fixtures are read as plain JSON trees, so no serialization compiler plugin is needed.
    testImplementation(libs.kotlinx.serialization.json)
}
