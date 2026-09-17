package com.jbgsoft.ambio.feature.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jbgsoft.ambio.media.AudioService

/**
 * Quick Settings toggle for the mix.
 *
 * The label is the app's name rather than the mix's: a tile is too narrow for
 * "Rain + Fireplace", and the system truncates without warning.
 */
class PlaybackTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        when (TileTap.decide(isPlaying = PlaybackFlag.isPlaying(this))) {
            TileTap.BROADCAST_TOGGLE -> sendBroadcast(playPauseIntent(this))
            TileTap.LAUNCH_PLAY -> launchPlay()
        }
        // The tile flips immediately rather than waiting for the service to come up and
        // report back: the user tapped it, and a control that lags its own tap reads as
        // broken. The next onStartListening corrects it if the service disagreed.
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    /**
     * API 34 replaced the Intent overload of startActivityAndCollapse with a PendingIntent
     * one; the Intent overload is reached only below 34, where the PendingIntent overload
     * does not exist; minSdk is 31.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchPlay() {
        // The category is how AudioService.onTaskRemoved tells this task apart from the
        // app's; explicit intents resolve by component, so it changes nothing else.
        val intent = Intent(this, TilePlayActivity::class.java)
            .addCategory(AudioService.CATEGORY_TILE_TRAMPOLINE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (PlaybackFlag.isPlaying(this@PlaybackTile)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}
