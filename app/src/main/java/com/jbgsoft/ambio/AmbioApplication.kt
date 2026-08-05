package com.jbgsoft.ambio

import android.app.Application
import com.jbgsoft.ambio.feature.tile.PlaybackFlag
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AmbioApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // This process is new, so AudioService — which runs in it — is not playing,
        // whatever the last value written was. AudioService.onDestroy clears the flag on an
        // orderly stop; the low-memory killer, a crash and force-stop all skip onDestroy and
        // would otherwise leave it true forever, leaving the tile showing Active over a
        // dead service and starting playback when tapped.
        PlaybackFlag.clearPlaying(this)
    }
}
