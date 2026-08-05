package com.jbgsoft.ambio.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jbgsoft.ambio.media.AudioService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Turns the service's playback broadcast into a widget refresh. */
class PlaybackStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AudioService.ACTION_PLAYBACK_CHANGED) return
        val playing = intent.getBooleanExtra(AudioService.EXTRA_IS_PLAYING, false)
        // goAsync() because updating a Glance widget suspends, and a receiver that returns
        // before its work finishes has its process eligible for death mid-update.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.setPlaying(context.applicationContext, playing)
            } finally {
                pending.finish()
            }
        }
    }
}
