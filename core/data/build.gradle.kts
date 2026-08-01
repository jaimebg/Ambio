plugins {
    id("ambio.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jbgsoft.ambio.core.data"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Compose (for Icons in Sound)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.icons.extended)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
