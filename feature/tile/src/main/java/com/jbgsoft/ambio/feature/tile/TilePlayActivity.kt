package com.jbgsoft.ambio.feature.tile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.jbgsoft.ambio.media.AudioService

/**
 * Starts the stored mix from the Quick Settings tile when the app is not running.
 *
 * This activity draws nothing (framework translucent theme, no content view) and
 * finishes inside onCreate. It exists because Android 12+ lets a foreground app
 * start a foreground service and refuses the same call from a background
 * broadcast receiver, which is where the tile's media-button broadcast lands
 * (issue #13). Being started from a tile makes the app user-visible for the
 * one call that matters.
 *
 * The intent is a media button rather than a custom action so that nothing new
 * is needed on the service side: MediaSessionService handles ACTION_MEDIA_BUTTON
 * in onStartCommand, play on an empty mix runs AudioService's stored-mix load,
 * and that load is what meets the startForeground deadline.
 */
class TilePlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(this, playIntent(this))
        finish()
    }
}

/** A KEYCODE_MEDIA_PLAY media-button intent aimed straight at [AudioService]. */
fun playIntent(context: Context): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        setClass(context, AudioService::class.java)
        putExtra(
            Intent.EXTRA_KEY_EVENT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        )
    }
