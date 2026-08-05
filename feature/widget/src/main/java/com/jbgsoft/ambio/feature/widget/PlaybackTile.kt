package com.jbgsoft.ambio.feature.widget

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
        sendBroadcast(playPauseIntent(this))
        // The tile flips immediately rather than waiting for the service to come up and
        // report back: the user tapped it, and a control that lags its own tap reads as
        // broken. The next onStartListening corrects it if the service disagreed.
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (WidgetUpdater.isPlaying(this@PlaybackTile)) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}
