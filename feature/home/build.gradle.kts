plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.feature.home"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":ui"))
    implementation(project(":media"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Hilt
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
