plugins {
    id("ambio.android.library")
    id("ambio.android.compose")
    id("ambio.android.hilt")
}

android {
    namespace = "com.jbgsoft.ambio.feature.widget"
}

dependencies {
    // Project modules
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Glance
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Media3, for MediaButtonReceiver and the media-button intent
    implementation(libs.bundles.media3)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)

    // Compose BOM, so the icons the test builds fixtures with (and the ImageVector type
    // Sound.icon carries) resolve to the same versions core:domain compiled against,
    // instead of the much older androidx.compose.ui:ui-graphics that Glance pulls in
    // transitively. Without this, WidgetDisplayTest fails to compile with "Unresolved
    // reference 'Icons'" and "Cannot access class 'ImageVector'".
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.icons.extended)
}
