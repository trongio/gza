plugins {
    alias(libs.plugins.gza.jvm.library)
    alias(libs.plugins.kover)
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

// The prediction rules are the app's headline feature; keep them near fully covered.
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
