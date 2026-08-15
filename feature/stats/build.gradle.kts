plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.feature.stats"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":ui"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Hilt
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    // core:common is test-only here: no production code in this module touches it,
    // LocaleDirectorySetTest just needs the Play-to-qualifier locale map.
    testImplementation(project(":core:common"))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
