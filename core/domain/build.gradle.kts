plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
}

android {
    namespace = "com.jbgsoft.ambio.core.domain"
}

dependencies {
    // Coroutines
    implementation(libs.bundles.coroutines)

    // Compose (for ImageVector in Sound model)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.icons.extended)

    // Javax Inject for @Inject annotation
    implementation(libs.javax.inject)

    // Testing
    testImplementation(libs.bundles.testing)
}
