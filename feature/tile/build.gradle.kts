plugins {
    id("ambio.android.library")
}

android {
    namespace = "com.jbgsoft.ambio.feature.tile"
}

dependencies {
    // For AudioService.ACTION_PLAYBACK_CHANGED / EXTRA_IS_PLAYING, and to target
    // AudioService by class from TilePlayActivity.
    implementation(project(":media"))

    // Media3, for MediaButtonReceiver and the media-button intent
    implementation(libs.bundles.media3)

    // ContextCompat.startForegroundService
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
