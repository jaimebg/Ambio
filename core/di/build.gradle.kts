plugins {
    id("ambio.android.library")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.core.di"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":media"))

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Media3
    implementation(libs.bundles.media3)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.bundles.testing)
    // RepositoryMixSource.currentMix() maps ActiveSound (core:domain), whose Sound.icon
    // and SoundTheme colors are androidx.compose.ui types declared `implementation` (not
    // `api`) in core:domain's own build.gradle.kts. That keeps them off this module's main
    // compile classpath, but a test that constructs a real Sound still needs them, so they
    // are added test-only here rather than promoted to `api` over there for one test file.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)
    testImplementation(libs.compose.icons.extended)
}
