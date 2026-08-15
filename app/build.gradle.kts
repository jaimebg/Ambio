import java.util.Properties

plugins {
    id("ambio.android.application")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

// Load keystore properties from local file (not committed to git)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.jbgsoft.ambio"

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.jbgsoft.ambio"
        versionCode = 3
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            // AmbioManifestTest reads the merged manifest through PackageManager, which
            // Robolectric only populates when the built resources are on the test runtime.
            isIncludeAndroidResources = true
        }
    }

    bundle {
        language {
            // Keep every language in the base APK. Play otherwise installs only the
            // splits matching the device's system locale, which would leave the
            // per-app language picker resolving to resources that are not present:
            // a user on an English device picking Japanese would keep seeing English.
            // The whole translatable surface is 67 strings and 3 plurals, so carrying
            // all of it costs far less than an on-demand split fetch would.
            enableSplit = false
        }
    }

    // AGP embeds a Google-specific protobuf of the dependency tree in the APK's
    // signing block by default. F-Droid's scanner treats it as an opaque blob and
    // refuses the APK outright: "found extra signing block 'Dependency metadata'".
    //
    // The two outputs are configured apart on purpose. Play reads this block to
    // raise security advisories about vulnerable dependencies, and that is worth
    // keeping, so the bundle it consumes still carries it. Only the APK — the
    // artifact F-Droid builds and ships — drops it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:di"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:tile"))
    implementation(project(":media"))
    implementation(project(":ui"))

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.bundles.lifecycle)

    // Hilt
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // DataStore
    implementation(libs.datastore.preferences)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // Media3
    implementation(libs.bundles.media3)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
