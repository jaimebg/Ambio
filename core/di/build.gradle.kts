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
}
