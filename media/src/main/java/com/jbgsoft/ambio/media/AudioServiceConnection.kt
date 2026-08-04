package com.jbgsoft.ambio.media

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages connection to the AudioService and provides a simple API for audio playback.
 * Handles connection lifecycle, playback control, and error handling.
 *
 * Note: Requires actual audio content in the raw resource files. Empty/placeholder
 * audio files will result in playback errors logged to Logcat.
 */
@Singleton
class AudioServiceConnection @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    // Volatile because the identity checks below are read from whichever thread finished
    // a connection (the completion listener runs on directExecutor()), and a guard that
    // compares against a stale copy of this field is not a guard.
    @Volatile
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var fadeJob: Job? = null
    private var targetVolume: Float = 1f

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    companion object {
        private const val TAG = "AudioServiceConnection"
        private const val FADE_IN_DURATION_MS = 8000L
        private const val FADE_OUT_DURATION_MS = 3000L
        private const val FADE_STEP_MS = 50L
    }

    val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone && !it.isCancelled }?.let {
            try {
                it.get()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get MediaController", e)
                null
            }
        }

    /**
     * How a controller is built. Production always builds the real one; the unit test
     * substitutes a future it can complete and dispatch by hand, because the two
     * identity checks in [connect] and [ConnectionListener.onDisconnected] only matter
     * across an interleaving a real future cannot be made to produce on one thread.
     */
    internal var buildController: (MediaController.Listener) -> ListenableFuture<MediaController> =
        { listener ->
            MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, AudioService::class.java))
            )
                .setListener(listener)
                .buildAsync()
        }

    fun connect() {
        if (controllerFuture != null) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        Log.d(TAG, "Connecting to AudioService...")

        // The listener has to be handed over before the future it belongs to exists, so
        // it is told which future that is on the line after. Neither of its callbacks
        // can run before buildController has returned.
        val listener = ConnectionListener()
        val future = buildController(listener)
        listener.own = future
        controllerFuture = future

        future.addListener({
            if (controllerFuture !== future) {
                // A superseded attempt finishing late: disconnect() cleared the field,
                // or a later connect() replaced it. Both branches below write the field,
                // and writing it here would strand whatever connection now owns it —
                // the else branch in particular would null a live future that nothing
                // holds a reference to and nothing will rebuild, leaving the app silent
                // until the process dies. A stale completion has to be inert.
                Log.d(TAG, "Ignoring the completion of a superseded connection attempt")
                return@addListener
            }
            val mediaController = controller
            if (mediaController != null) {
                Log.d(TAG, "Connected to AudioService")
                _isConnected.value = true
                _hasError.value = false
                mediaController.addListener(playerListener)
                _isPlaying.value = mediaController.isPlaying
            } else {
                Log.e(TAG, "Failed to connect to AudioService")
                // Never leave a failed future behind: connect() early-returns while one
                // is set, so keeping it would make every later attempt a silent no-op
                // forever. Clearing it is inert — nothing in this class calls connect(),
                // so only an external caller can try again, and each such call is one
                // attempt. This branch is where an automatic rebuild comes to rest.
                controllerFuture = null
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from AudioService")
        // Clear the flag first. Releasing the controller dispatches onDisconnected, and
        // the automatic rebuild there is gated on this flag — lowering it up front is
        // what tells a deliberate teardown apart from a connection we lost.
        _isConnected.value = false
        _isPlaying.value = false
        _hasError.value = false
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    /**
     * Declares the whole mix — every active sound, its level, and the title the media
     * notification shows. Idempotent: re-sending the same mix is a no-op service-side,
     * so callers re-assert rather than track what the service currently holds.
     *
     * Sounds are described by raw resource, not by anything domain-shaped: media does
     * not depend on core:domain.
     */
    fun setMix(mix: List<MixEntry>, title: String) {
        send(MixCommands.SET_MIX, MixBundle.encode(mix, title))
    }

    private fun send(action: String, args: Bundle) {
        val mediaController = controller
        if (mediaController == null) {
            Log.w(TAG, "Cannot send $action - controller not connected")
            return
        }
        mediaController.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }

    fun play() {
        fadeJob?.cancel()
        Log.d(TAG, "Play requested")
        controller?.apply {
            volume = 0f
            play()
            fadeIn(targetVolume)
        } ?: Log.w(TAG, "Cannot play - controller not connected")
    }

    fun pause() {
        Log.d(TAG, "Pause requested with fade out")
        _isPlaying.value = false  // Update UI immediately
        fadeOut {
            controller?.pause()
            // Restore volume for next play
            controller?.volume = targetVolume
        }
    }

    fun stop() {
        Log.d(TAG, "Stop requested with fade out")
        _isPlaying.value = false  // Update UI immediately
        fadeOut {
            controller?.stop()
            // Restore volume for next play
            controller?.volume = targetVolume
        }
    }

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        targetVolume = clampedVolume
        Log.d(TAG, "Setting target volume to $clampedVolume")
        // Only apply immediately if not fading
        if (fadeJob?.isActive != true) {
            controller?.volume = clampedVolume
        }
    }

    private fun fadeIn(toVolume: Float) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = (FADE_IN_DURATION_MS / FADE_STEP_MS).toInt()
            val startVolume = 0f
            val volumeStep = (toVolume - startVolume) / steps

            controller?.volume = startVolume
            Log.d(TAG, "Starting fade in to $toVolume over ${FADE_IN_DURATION_MS}ms")

            for (i in 1..steps) {
                delay(FADE_STEP_MS)
                val newVolume = (startVolume + volumeStep * i).coerceIn(0f, toVolume)
                controller?.volume = newVolume
            }
            controller?.volume = toVolume
            Log.d(TAG, "Fade in complete")
        }
    }

    private fun fadeOut(onComplete: () -> Unit) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val currentVolume = controller?.volume ?: targetVolume
            val steps = (FADE_OUT_DURATION_MS / FADE_STEP_MS).toInt()
            val volumeStep = currentVolume / steps

            Log.d(TAG, "Starting fade out from $currentVolume over ${FADE_OUT_DURATION_MS}ms")

            for (i in 1..steps) {
                delay(FADE_STEP_MS)
                val newVolume = (currentVolume - volumeStep * i).coerceAtLeast(0f)
                controller?.volume = newVolume
            }
            controller?.volume = 0f
            Log.d(TAG, "Fade out complete")
            onComplete()
        }
    }

    /**
     * Without this, [_isConnected] only ever went false in [disconnect] — a service
     * that died or was rebound left the flag stuck at true, so observers waiting for
     * the connection to come back up were never told it had gone down, and the mix was
     * never re-pushed. Falling is only half of it though: nothing else in the app calls
     * [connect], so the flag also has to be able to rise again.
     *
     * Exactly one rebuild per connection lost, and it cannot become a retry loop:
     *
     *  - The attempt is gated on [_isConnected] having been true, and that flag is set
     *    true in exactly one place — after a controller was successfully obtained. So a
     *    rebuild is only ever attempted for a connection that had been established.
     *  - The flag is lowered *before* the attempt. If the rebuild fails, it is never
     *    raised again, so any later onDisconnected sees false and does nothing.
     *  - Reaching N automatic attempts therefore requires N successful connections that
     *    were each subsequently lost. That is real service churn, not this code
     *    hammering: between any two attempts there is a connection that worked.
     *  - A failed rebuild leaves controllerFuture null (see [connect]), so the class is
     *    quiescent but not wedged: a later external [connect] can still succeed.
     *
     * The same gate means [disconnect]'s own release, which also dispatches here, is
     * correctly read as deliberate and not rebuilt.
     *
     * One listener is built per connection attempt, and it acts only while [own] — the
     * future it was created alongside — is still the one this class holds. The
     * controller that dispatched a stale callback is by definition not the live one, so
     * clearing the field for it would strand a connection nobody can reach and nothing
     * will rebuild.
     */
    private inner class ConnectionListener : MediaController.Listener {

        @Volatile
        var own: ListenableFuture<MediaController>? = null

        override fun onDisconnected(controller: MediaController) {
            val ownFuture = own
            if (ownFuture == null || ownFuture !== controllerFuture) {
                Log.d(TAG, "Ignoring onDisconnected from a superseded connection")
                return
            }
            val wasConnected = _isConnected.value
            Log.w(TAG, "Disconnected from AudioService (wasConnected=$wasConnected)")
            controllerFuture = null
            _isConnected.value = false
            _isPlaying.value = false
            if (wasConnected) connect()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Playback state changed: isPlaying=$isPlaying")
            _isPlaying.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback error: ${error.errorCodeName} - ${error.message}", error)
            _hasError.value = true
            _isPlaying.value = false

            // Common causes:
            // - Empty audio file (ERROR_CODE_IO_FILE_NOT_FOUND or ERROR_CODE_PARSING_CONTAINER_MALFORMED)
            // - Unsupported format (ERROR_CODE_DECODER_INIT_FAILED)
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    Log.e(TAG, "Audio file may be empty or corrupted. " +
                            "Ensure actual audio content exists in the raw resource files.")
                }
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                    Log.e(TAG, "Audio format may not be supported. " +
                            "Use .ogg (Vorbis) or .mp3 format for best compatibility.")
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            Log.d(TAG, "Playback state: $stateName")
        }
    }
}
