plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
}

android {
    namespace = "com.jbgsoft.ambio.storeassets"

    sourceSets {
        getByName("test") {
            res.directories.add("src/test/res")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            // Fifteen full-resolution bitmaps per run, the largest 2560x1600,
            // plus Robolectric's own graphics. The default heap does not hold it.
            all { it.maxHeapSize = "4g" }
        }
    }
}

// Everything here exists to render real screens into PNGs on the JVM. It is a
// test-only module: :app does not depend on it, so none of this reaches the
// shipped bundle even though it links against the feature modules.
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:stats"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
