package com.jbgsoft.ambio.feature.widget

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

/**
 * The one intent both the widget and the tile send.
 *
 * Neither surface builds a MediaController: they are ephemeral processes, and
 * MediaController.buildAsync can outlive them, which fails intermittently rather than
 * cleanly. A media-button intent needs no connection at all — Media3's MediaButtonReceiver
 * routes it to AudioService and starts the service if it is not already running, which is
 * what makes pressing play work hours after the app was last opened.
 *
 * MediaButtonReceiver is @UnstableApi in Media3 1.10.1, so referencing it needs an explicit
 * opt-in. @OptIn rather than @UnstableApi, and on this function rather than the file: the
 * marker annotation would propagate to PlaybackTile and AmbioWidget and from there to
 * anything that touches them — the same cascade AudioService avoids for the same reason.
 * Nothing unstable appears in this signature, only in the body, so the opt-in stops here.
 */
@OptIn(markerClass = [UnstableApi::class])
fun playPauseIntent(context: Context): Intent =
    Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        setClass(context, MediaButtonReceiver::class.java)
        putExtra(
            Intent.EXTRA_KEY_EVENT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }
