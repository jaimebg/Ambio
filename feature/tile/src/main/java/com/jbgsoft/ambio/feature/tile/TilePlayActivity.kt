package com.jbgsoft.ambio.feature.tile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * The intent carries [AudioService.ACTION_PLAY_STORED_MIX], an explicit service action
 * that [AudioService]'s onStartCommand handles directly. A media-button intent was
 * tried first and rejected: Media3 1.10.1 intercepts a play key before it ever reaches
 * the player when that player holds no media item, so on the cold-start empty player
 * the stored-mix load never runs, startForeground() never happens, and the system
 * kills the service with ForegroundServiceDidNotStartInTimeException.
 */
class TilePlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(this, playIntent(this))
        finish()
    }
}

/** An explicit [AudioService.ACTION_PLAY_STORED_MIX] service intent aimed at [AudioService]. */
fun playIntent(context: Context): Intent =
    Intent(context, AudioService::class.java).setAction(AudioService.ACTION_PLAY_STORED_MIX)
