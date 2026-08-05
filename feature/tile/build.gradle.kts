plugins {
    id("ambio.android.library")
}

android {
    namespace = "com.jbgsoft.ambio.feature.tile"
}

dependencies {
    // For AudioService.ACTION_PLAYBACK_CHANGED / EXTRA_IS_PLAYING.
    implementation(project(":media"))

    // Media3, for MediaButtonReceiver and the media-button intent
    implementation(libs.bundles.media3)
}
