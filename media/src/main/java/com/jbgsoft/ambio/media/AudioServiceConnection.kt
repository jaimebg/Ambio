package com.jbgsoft.ambio.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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
    @ApplicationContext private val context: Context
) {
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

    fun connect() {
        if (controllerFuture != null) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        Log.d(TAG, "Connecting to AudioService...")
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudioService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            val mediaController = controller
            if (mediaController != null) {
                Log.d(TAG, "Connected to AudioService")
                _isConnected.value = true
                _hasError.value = false
                mediaController.addListener(playerListener)
                _isPlaying.value = mediaController.isPlaying
            } else {
                Log.e(TAG, "Failed to connect to AudioService")
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from AudioService")
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _isConnected.value = false
        _isPlaying.value = false
        _hasError.value = false
    }

    fun playSound(
        @RawRes audioRes: Int,
        name: String,
        description: String,
        @RawRes illustrationRes: Int? = null
    ) {
        fadeJob?.cancel()
        val soundUri = Uri.parse("android.resource://${context.packageName}/$audioRes")
        val artworkUri = illustrationRes?.let {
            Uri.parse("android.resource://${context.packageName}/$it")
        }

        Log.d(TAG, "Playing sound: $name (uri=$soundUri)")

        val mediaItem = MediaItem.Builder()
            .setUri(soundUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(description)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()

        controller?.apply {
            _hasError.value = false
            volume = 0f  // Start silent for fade in
            setMediaItem(mediaItem)
            prepare()
            play()
            fadeIn(targetVolume)
        } ?: Log.w(TAG, "Cannot play sound - controller not connected")
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
