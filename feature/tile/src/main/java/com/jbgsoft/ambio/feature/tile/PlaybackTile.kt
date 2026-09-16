package com.jbgsoft.ambio.feature.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

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
     * API 34 replaced the Intent overload of startActivityAndCollapse with a
     * PendingIntent one; the Intent overload still works below 34 and minSdk is 31.
     * The Intent overload is reached only below API 34, where the PendingIntent overload does not exist.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchPlay() {
        val intent = Intent(this, TilePlayActivity::class.java)
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
