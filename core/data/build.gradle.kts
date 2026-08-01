plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.core.data"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Compose (for Icons in Sound)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.icons.extended)

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
