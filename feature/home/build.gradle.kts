plugins {
    id("ambio.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jbgsoft.ambio.feature.home"

    buildFeatures {
        compose = true
    }
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
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
