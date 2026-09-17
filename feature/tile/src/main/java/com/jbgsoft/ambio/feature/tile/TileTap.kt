package com.jbgsoft.ambio.feature.tile

/**
 * What a tile tap does, decided from the last known playback state.
 *
 * A broadcast to Media3's MediaButtonReceiver can only start AudioService when
 * the app is already a foreground process: from a background receiver, Android 12+
 * refuses the startForegroundService call and Media3 swallows the exception
 * (issue #13). While the mix plays, the service is foreground and the broadcast
 * works, and it leaves the Quick Settings panel open, which is the nicer pause.
 *
 * When nothing plays, the tile goes through TilePlayActivity instead: an activity
 * started from a tile is user-visible, and a user-visible app may start a
 * foreground service. The panel collapses, as it does for any tile that starts
 * an activity.
 */
enum class TileTap {
    BROADCAST_TOGGLE,
    LAUNCH_PLAY;

    companion object {
        fun decide(isPlaying: Boolean): TileTap =
            if (isPlaying) BROADCAST_TOGGLE else LAUNCH_PLAY
    }
}
