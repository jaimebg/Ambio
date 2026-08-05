package com.jbgsoft.ambio.feature.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jbgsoft.ambio.media.AudioService

/** Turns the service's playback broadcast into an update of [PlaybackFlag], for the tile. */
class PlaybackStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AudioService.ACTION_PLAYBACK_CHANGED) return
        val playing = intent.getBooleanExtra(AudioService.EXTRA_IS_PLAYING, false)
        PlaybackFlag.setPlaying(context.applicationContext, playing)
    }
}
