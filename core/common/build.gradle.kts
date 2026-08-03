plugins {
    id("ambio.android.library")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.core.common"
}

dependencies {
    // Coroutines
    implementation(libs.bundles.coroutines)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
}
