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

    // For AudioService.ACTION_PLAYBACK_CHANGED / EXTRA_IS_PLAYING. The dependency points
    // this way — widget -> media — because media declares no project dependencies at all,
    // and this module pulls core:domain. feature:home already depends on :media.
    implementation(project(":media"))

    // Compose BOM, so androidx.compose.ui:ui-graphics (and everything else Compose)
    // resolves to the project's pinned versions on this module's own compile classpath,
    // instead of the much older versions Glance pins and pulls in transitively. Without
    // this, the ImageVector type Sound.icon carries — and any Color/ColorProvider a
    // Glance composable in this module references directly — resolve to Glance's old
    // 1.1.1 copy instead of the one core:domain compiled against. core:domain and
    // feature:stats both add this for the same reason; this follows the same pattern.
    implementation(platform(libs.compose.bom))

    // Glance
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Media3, for MediaButtonReceiver and the media-button intent
    implementation(libs.bundles.media3)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.bundles.testing)

    // material-icons-extended, for the ImageVector fixtures WidgetDisplayTest builds via
    // Sound(icon = Icons.Default.WaterDrop, ...). The compose.bom above already
    // constrains this module's unit-test compile classpath too — AGP's unit-test compile
    // classpath extends this module's own `implementation` configuration, which is why
    // glance-appwidget above was already visible on debugUnitTestCompileClasspath before
    // this dependency existed. A separate testImplementation(platform(...)) is therefore
    // not needed; only the extra library itself is.
    testImplementation(libs.compose.icons.extended)
}
