package com.jbgsoft.ambio.media

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint

/**
 * Opted in wholesale rather than at each call site: MixPlayer, the SimpleBasePlayer
 * it extends and SessionError are all @UnstableApi in Media3 1.10.1.
 *
 * @OptIn rather than @UnstableApi: the latter would mark AudioService itself as
 * unstable and force the same marker onto AudioServiceConnection, and from there
 * onto every caller of it.
 */
@OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class AudioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: MixPlayer

    /**
     * Pauses the whole mix when the audio output stops being a private one — headphones
     * unplugged, Bluetooth headset disconnected.
     *
     * This lives here rather than in MixPlayer for two reasons. It is a *broadcast*, not
     * an audio-focus change: unplugging a headset never fires OnAudioFocusChangeListener,
     * so MixPlayer's focus handling cannot see it and does not cover it. And receiving a
     * broadcast needs a Context, which MixPlayer deliberately does not take — AudioService
     * already has one.
     *
     * One receiver for the whole mix, not one per track: five ExoPlayers each pausing
     * themselves would be five uncoordinated decisions about a single logical playback.
     */
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player.pause()
            }
        }
    }

    private fun broadcastPlayback(isPlaying: Boolean) {
        sendBroadcast(
            Intent(ACTION_PLAYBACK_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
        )
    }

    private val playbackBroadcaster = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = broadcastPlayback(isPlaying)
    }

    override fun onCreate() {
        super.onCreate()

        player = MixPlayer(mainLooper, { ExoPlayerSoundTrack(this) }, AndroidAudioFocus(this))
        player.addListener(playbackBroadcaster)

        // NOT_EXPORTED: ACTION_AUDIO_BECOMING_NOISY is a protected broadcast, so only the
        // system can ever send it — and protected system broadcasts still reach a
        // not-exported receiver. Nothing legitimate is turned away by refusing other apps.
        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Create PendingIntent to launch MainActivity when notification is tapped
        // This is required for Media3 to properly manage foreground service and notifications
        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .apply {
                sessionActivityPendingIntent?.let { setSessionActivity(it) }
            }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // The last thing this service says. Without it the widget keeps showing a pause
        // button over a service that no longer exists, and keeps showing it forever.
        broadcastPlayback(false)
        unregisterReceiver(becomingNoisyReceiver)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = SessionCommands.Builder()
                .add(SessionCommand(MixCommands.SET_MIX, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                MixCommands.SET_MIX -> if (!applySetMix(player, args)) {
                    // Refused, not applied: a bundle we cannot read must leave the mix
                    // alone rather than resolve to "no sounds" and silence everything.
                    Log.w(TAG, "Ignoring malformed ${MixCommands.SET_MIX}")
                    return Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_BAD_VALUE)
                    )
                }
                // @SessionResult.Code is declared in terms of SessionError's constants in
                // 1.10.1; SessionResult.RESULT_ERROR_NOT_SUPPORTED holds the same value
                // but is outside the IntDef, and lint rejects it.
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        private const val TAG = "AudioService"

        /**
         * Broadcast when playback starts or stops, and once more as the service dies.
         *
         * A broadcast rather than a direct call because media must not depend on any
         * feature module: it declares no project dependencies at all today, and the widget
         * module pulls core:domain, which this module is not allowed to reach.
         */
        const val ACTION_PLAYBACK_CHANGED = "com.jbgsoft.ambio.PLAYBACK_CHANGED"
        const val EXTRA_IS_PLAYING = "is_playing"
    }
}
