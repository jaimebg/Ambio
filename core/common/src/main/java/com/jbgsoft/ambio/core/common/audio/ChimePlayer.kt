package com.jbgsoft.ambio.core.common.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple one-shot audio player for notification sounds like timer completion chimes.
 * Uses Android's MediaPlayer for straightforward playback without the complexity
 * of MediaSession (which is designed for continuous background audio with controls).
 */
@Singleton
class ChimePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Play a one-shot sound from raw resources.
     * Any previously playing chime will be stopped and released.
     *
     * @param audioRes The raw resource ID of the audio file to play
     */
    fun playChime(@RawRes audioRes: Int) {
        // Release any existing player
        release()

        try {
            mediaPlayer = MediaPlayer.create(context, audioRes)?.apply {
                setOnCompletionListener { mp ->
                    mp.release()
                    mediaPlayer = null
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    true
                }
                start()
            }
        } catch (e: Exception) {
            // Log error but don't crash - chime is non-critical
            android.util.Log.e("ChimePlayer", "Failed to play chime", e)
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
