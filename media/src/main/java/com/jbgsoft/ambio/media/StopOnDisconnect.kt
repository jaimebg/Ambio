package com.jbgsoft.ambio.media

import androidx.media3.common.Player

/**
 * Whether AudioService should stop itself when a controller disconnects.
 *
 * Backing out of the app finishes the activity and releases its controller, but
 * nothing removes the task, so onTaskRemoved never fires and the service lives on
 * with nothing to do (issue #9). This is the back-out counterpart.
 *
 * Only STATE_IDLE counts: that is what MixPlayer reports once Stop has released
 * every track. A paused mix is STATE_READY and keeps its notification, and a
 * process behind it, so the user can resume from the notification or the tile.
 *
 * Media3's own notification connects as a controller and comes and goes on its
 * own schedule; it must never be the trigger.
 */
internal fun shouldStopOnDisconnect(
    isNotificationController: Boolean,
    @Player.State playbackState: Int
): Boolean = !isNotificationController && playbackState == Player.STATE_IDLE
