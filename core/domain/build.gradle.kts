plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jbgsoft.ambio.core.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
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
