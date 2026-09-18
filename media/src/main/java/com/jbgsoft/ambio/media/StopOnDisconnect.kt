package com.jbgsoft.ambio.media

import androidx.media3.common.Player

/**
 * Whether AudioService should stop itself when a controller disconnects.
 *
 * Backing out of the app finishes the activity and releases its controller, but
 * nothing removes the task, so onTaskRemoved never fires and the service lives on
 * with nothing to do (issue #9). This is the back-out counterpart, and it asks the
 * same question onTaskRemoved does: is anything meant to be sounding?
 *
 * Idle is what MixPlayer reports once Stop has released every track. Paused is
 * STATE_READY with playWhenReady false, and it counts too. 2.1 kept a paused mix
 * alive so it could be resumed from its notification, but Ambient mode has no Stop,
 * only Pause, so there that meant the service never went away at all. The tile can
 * start the stored mix from cold, so nothing is lost by letting go.
 *
 * Only the app's own controller is a signal. Media3's notification connects as a
 * controller and comes and goes on its own schedule, and so do System UI, headsets
 * and watches, all of them while the app may still be open.
 */
internal fun shouldStopOnDisconnect(
    isAppController: Boolean,
    isNotificationController: Boolean,
    @Player.State playbackState: Int,
    playWhenReady: Boolean
): Boolean = isAppController &&
    !isNotificationController &&
    (playbackState == Player.STATE_IDLE || !playWhenReady)
