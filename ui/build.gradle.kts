plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
}

android {
    namespace = "com.jbgsoft.ambio.ui"
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

    // Lifecycle — repeatOnLifecycle and LocalLifecycleOwner for the effects overlay
    implementation(libs.bundles.lifecycle)

    // Testing
    testImplementation(libs.bundles.testing)
}
