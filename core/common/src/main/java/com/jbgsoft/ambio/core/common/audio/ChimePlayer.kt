package com.jbgsoft.ambio.core.common.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple one-shot audio player for notification sounds like timer completion chimes.
 * Uses Android's MediaPlayer for straightforward playback without the complexity
 * of MediaSession (which is designed for continuous background audio with controls).
 *
 * Includes a robust fallback chain:
 * 1. Try custom raw resource audio file
 * 2. Fall back to system alarm sound
 * 3. Fall back to system notification sound
 * 4. Fall back to system ringtone
 *
 * This ensures timer completion is always audible even if custom audio files are missing.
 */
@Singleton
class ChimePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "ChimePlayer"
    }

    /**
     * Play a one-shot sound from raw resources with system sound fallback.
     * Any previously playing chime will be stopped and released.
     *
     * @param audioRes The raw resource ID of the audio file to play
     */
    fun playChime(@RawRes audioRes: Int) {
        // Release any existing player
        release()

        // Try custom resource first
        if (tryPlayRawResource(audioRes)) {
            Log.d(TAG, "Playing custom chime from raw resource")
            return
        }

        // Fallback: Try system alarm sound (appropriate for timer completion)
        if (tryPlaySystemSound(RingtoneManager.TYPE_ALARM)) {
            Log.d(TAG, "Playing system alarm sound as fallback")
            return
        }

        // Fallback: Try system notification sound
        if (tryPlaySystemSound(RingtoneManager.TYPE_NOTIFICATION)) {
            Log.d(TAG, "Playing system notification sound as fallback")
            return
        }

        // Fallback: Try system ringtone
        if (tryPlaySystemSound(RingtoneManager.TYPE_RINGTONE)) {
            Log.d(TAG, "Playing system ringtone as fallback")
            return
        }

        Log.w(TAG, "Failed to play any sound - no fallback available")
    }

    /**
     * Attempt to play audio from a raw resource.
     *
     * @return true if playback started successfully, false otherwise
     */
    private fun tryPlayRawResource(@RawRes audioRes: Int): Boolean {
        return try {
            val player = MediaPlayer.create(context, audioRes)
            if (player == null) {
                Log.d(TAG, "MediaPlayer.create returned null for raw resource")
                return false
            }

            // Check if the player has valid duration (0 means empty file)
            if (player.duration <= 0) {
                Log.d(TAG, "Raw resource has no duration (likely empty file)")
                player.release()
                return false
            }

            mediaPlayer = player.apply {
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    true
                }
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play raw resource", e)
            false
        }
    }

    /**
     * Attempt to play a system sound by type.
     *
     * @param ringtoneType One of RingtoneManager.TYPE_ALARM, TYPE_NOTIFICATION, or TYPE_RINGTONE
     * @return true if playback started successfully, false otherwise
     */
    private fun tryPlaySystemSound(ringtoneType: Int): Boolean {
        return try {
            // Use getActualDefaultRingtoneUri to avoid FileNotFoundException
            val soundUri = RingtoneManager.getActualDefaultRingtoneUri(context, ringtoneType)
                ?: RingtoneManager.getDefaultUri(ringtoneType)

            if (soundUri == null) {
                Log.d(TAG, "No system sound available for type $ringtoneType")
                return false
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, soundUri)
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "System sound playback error: what=$what, extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play system sound type $ringtoneType", e)
            false
        }
    }

    /**
     * Stop any currently playing chime and release resources.
     */
    fun release() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
        mediaPlayer = null
    }
}
