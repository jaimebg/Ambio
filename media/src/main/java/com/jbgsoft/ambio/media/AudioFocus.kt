package com.jbgsoft.ambio.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** What the system told us about our hold on audio focus. */
enum class FocusChange { LOST, LOST_TRANSIENT, LOST_TRANSIENT_DUCK, GAINED }

/**
 * Audio focus for the whole mix, as one narrow seam.
 *
 * Narrow for the same reason [SoundTrack] is: MixPlayer must not take a Context, or it
 * stops being unit-testable on the JVM, and this class has no other coverage available.
 *
 * Before this existed, each ExoPlayer requested focus for itself. The system keeps one
 * entry per client, so five players of the same app evicted one another and only the
 * most recently added sound kept playing.
 */
interface AudioFocus {
    /** @return true if focus was granted; the mix must not play without it. */
    fun request(): Boolean
    fun abandon()
    fun onChange(listener: (FocusChange) -> Unit)
}

class AndroidAudioFocus(context: Context) : AudioFocus {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var listener: ((FocusChange) -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val mapped = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAINED
            AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOST
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOST_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LOST_TRANSIENT_DUCK
            else -> return@OnAudioFocusChangeListener
        }
        listener?.invoke(mapped)
    }

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()

    override fun request(): Boolean =
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }

    override fun onChange(listener: (FocusChange) -> Unit) {
        this.listener = listener
    }
}
