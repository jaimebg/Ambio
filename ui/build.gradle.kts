plugins {
    id("ambio.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jbgsoft.ambio.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
