plugins {
    id("ambio.android.library")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.media"
}

dependencies {
    // Coroutines
    implementation(libs.bundles.coroutines)

    // Media3
    implementation(libs.bundles.media3)

    // Lifecycle Service
    implementation(libs.lifecycle.service)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
}
