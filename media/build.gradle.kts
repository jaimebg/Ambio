plugins {
    id("ambio.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jbgsoft.ambio.media"
}

dependencies {
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Media3
    implementation(libs.bundles.media3)

    // Lifecycle Service
    implementation(libs.lifecycle.service)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
}
