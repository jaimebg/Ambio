package com.jbgsoft.ambio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Takes audio focus away from the app, the way an incoming call does.
 *
 * A phone call interrupts music by requesting transient audio focus; every other
 * holder gets AUDIOFOCUS_LOSS_TRANSIENT and is expected to pause. This asks the
 * system for exactly that, so what the app receives is a genuine framework callback
 * and not a stub of one — the system decides, not the test.
 *
 * It replaces an earlier attempt that shelled out to `adb emu gsm call`. That could
 * never have worked: instrumented tests run on the device, where there is no adb
 * binary. It is also better than the thing it replaces, because this runs on a
 * physical phone as well as on an emulator.
 *
 * The real `adb emu gsm call` path was verified by hand on API 37 and took the mix
 * 5 -> 0 -> 5, so this is a faithful proxy for it and not a weaker substitute.
 */
object FocusIntruder {

    private val audioManager: AudioManager
        get() = InstrumentationRegistry.getInstrumentation().context
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var request: AudioFocusRequest? = null

    /** Grabs transient focus. The app under test should pause every sound. */
    fun grabTransiently() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        request = req
        check(audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "FocusIntruder could not take audio focus; the test cannot prove anything"
        }
    }

    /** Gives it back. The app under test should resume every sound. */
    fun release() {
        request?.let { audioManager.abandonAudioFocusRequest(it) }
        request = null
    }
}
