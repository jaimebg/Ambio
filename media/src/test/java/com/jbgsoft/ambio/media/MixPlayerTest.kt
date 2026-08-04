package com.jbgsoft.ambio.media

import android.os.Looper
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MixPlayer is deliberately built on a narrow SoundTrack interface rather than on
 * Player, so the mixing logic can be tested without a device, a decoder, or an
 * audio file. Robolectric supplies only the Looper that SimpleBasePlayer requires.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MixPlayerTest {

    private class FakeTrack : SoundTrack {
        var startedWith: Int? = null
        var appliedVolume: Float = 0f
        var paused = false
        var released = false

        override fun start(audioRes: Int) { startedWith = audioRes }
        override fun setVolume(level: Float) { appliedVolume = level }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun release() { released = true }
    }

    private class FakeFocus : AudioFocus {
        var granted = true
        var requests = 0
        var abandons = 0
        private var listener: ((FocusChange) -> Unit)? = null

        override fun request(): Boolean { requests++; return granted }
        override fun abandon() { abandons++ }
        override fun onChange(listener: (FocusChange) -> Unit) { this.listener = listener }

        /** Drives the player the way the system would. */
        fun emit(change: FocusChange) { listener?.invoke(change) }
    }

    private val tracks = mutableMapOf<String, FakeTrack>()
    private val focus = FakeFocus()

    private fun player(): MixPlayer =
        MixPlayer(Looper.getMainLooper(), { id -> FakeTrack().also { tracks[id] = it } }, focus)

    @Test
    fun `activating a sound starts a track for it`() {
        val mix = player()

        mix.setSoundActive("rain", audioRes = 42, active = true)

        assertThat(tracks["rain"]!!.startedWith).isEqualTo(42)
    }

    @Test
    fun `deactivating a sound releases its track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 42, active = true)

        mix.setSoundActive("rain", audioRes = 42, active = false)

        assertThat(tracks["rain"]!!.released).isTrue()
    }

    @Test
    fun `five sounds play at once`() {
        val mix = player()

        listOf("rain", "fireplace", "forest", "ocean", "cave")
            .forEachIndexed { index, id -> mix.setSoundActive(id, audioRes = index, active = true) }

        assertThat(tracks.values.count { !it.released }).isEqualTo(5)
    }

    @Test
    fun `a track's volume is its own level times the master`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.setSoundLevel("ocean", 0.5f)
        mix.volume = 0.4f

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.4f)
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `pausing pauses every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()

        mix.pause()

        assertThat(tracks.values.all { it.paused }).isTrue()
    }

    @Test
    fun `playing resumes every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.pause()

        mix.play()

        assertThat(tracks["rain"]!!.paused).isFalse()
    }

    @Test
    fun `a sound activated while playing starts unpaused`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]!!.paused).isFalse()
    }

    @Test
    fun `a sound activated while paused starts paused`() {
        val mix = player()

        mix.setSoundActive("rain", audioRes = 1, active = true)

        assertThat(tracks["rain"]!!.paused).isTrue()
    }

    @Test
    fun `stopping releases every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.stop()

        assertThat(tracks.values.all { it.released }).isTrue()
    }

    @Test
    fun `the player reports playing once a sound is active and play was called`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.play()

        assertThat(mix.playWhenReady).isTrue()
        assertThat(mix.playbackState).isEqualTo(Player.STATE_READY)
    }

    @Test
    fun `an empty mix is idle`() {
        assertThat(player().playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `the mix title reaches the media metadata`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.setMixTitle("Rain + Fireplace")

        assertThat(mix.mediaMetadata.title.toString()).isEqualTo("Rain + Fireplace")
    }

    @Test
    fun `activating the same sound twice does not restart it`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        val first = tracks["rain"]

        mix.setSoundActive("rain", audioRes = 1, active = true)

        assertThat(tracks["rain"]).isSameInstanceAs(first)
        assertThat(first!!.released).isFalse()
    }

    // --- setMix: the full-state command the session actually exposes ---

    @Test
    fun `setMix releases sounds that are no longer in the mix`() {
        val mix = player()
        mix.setMix(
            listOf(MixEntry("rain", 1, 1f), MixEntry("ocean", 2, 1f)),
            title = "Rain + Ocean"
        )

        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")

        assertThat(tracks["ocean"]!!.released).isTrue()
        assertThat(tracks["rain"]!!.released).isFalse()
    }

    @Test
    fun `setMix starts newly added sounds and applies their levels`() {
        val mix = player()
        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")

        mix.setMix(
            listOf(MixEntry("rain", 1, 0.5f), MixEntry("forest", 7, 0.25f)),
            title = "Rain + Forest"
        )

        assertThat(tracks["forest"]!!.startedWith).isEqualTo(7)
        assertThat(tracks["forest"]!!.appliedVolume).isWithin(0.001f).of(0.25f)
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `setMix does not restart sounds that are already playing`() {
        val mix = player()
        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")
        val first = tracks["rain"]

        mix.setMix(
            listOf(MixEntry("rain", 1, 1f), MixEntry("ocean", 2, 1f)),
            title = "Rain + Ocean"
        )

        assertThat(tracks["rain"]).isSameInstanceAs(first)
        assertThat(first!!.released).isFalse()
    }

    @Test
    fun `setMix rebuilds the mix after stop released every track`() {
        // The Player contract lets stop() clear everything; the mix has to be
        // re-declarable, or "stop then play" is silence.
        val mix = player()
        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")
        mix.play()
        mix.stop()
        assertThat(mix.playbackState).isEqualTo(Player.STATE_IDLE)

        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")
        mix.play()

        assertThat(tracks["rain"]!!.startedWith).isEqualTo(1)
        assertThat(tracks["rain"]!!.released).isFalse()
        assertThat(tracks["rain"]!!.paused).isFalse()
        assertThat(mix.playbackState).isEqualTo(Player.STATE_READY)
    }

    @Test
    fun `setMix carries the title into the media metadata`() {
        val mix = player()

        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain + Fireplace")

        assertThat(mix.mediaMetadata.title.toString()).isEqualTo("Rain + Fireplace")
    }

    @Test
    fun `setMix after release does not create or start a track`() {
        val mix = player()
        mix.release()

        mix.setMix(listOf(MixEntry("ocean", 2, 1f)), title = "Ocean")

        assertThat(tracks["ocean"]).isNull()
    }

    @Test
    fun `activating a sound after release does not create or start a track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.release()

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]).isNull()
    }

    // --- audio focus ---

    @Test
    fun `playing requests audio focus once for the whole mix`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.play()

        assertThat(focus.requests).isEqualTo(1)
    }

    @Test
    fun `a denied focus request leaves the mix paused`() {
        focus.granted = false
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.play()

        assertThat(mix.playWhenReady).isFalse()
        assertThat(tracks["rain"]!!.paused).isTrue()
    }

    @Test
    fun `a transient loss pauses every track and keeps the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()

        focus.emit(FocusChange.LOST_TRANSIENT)

        assertThat(tracks.values.all { it.paused }).isTrue()
        assertThat(focus.abandons).isEqualTo(0)
    }

    @Test
    fun `regaining focus after a transient loss resumes every track`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        focus.emit(FocusChange.LOST_TRANSIENT)

        focus.emit(FocusChange.GAINED)

        assertThat(tracks.values.none { it.paused }).isTrue()
    }

    @Test
    fun `regaining focus does not resume a mix the user paused`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.pause()

        focus.emit(FocusChange.GAINED)

        assertThat(tracks["rain"]!!.paused).isTrue()
        assertThat(mix.playWhenReady).isFalse()
    }

    @Test
    fun `a permanent loss pauses the mix and abandons the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        focus.emit(FocusChange.LOST)

        assertThat(tracks["rain"]!!.paused).isTrue()
        assertThat(focus.abandons).isEqualTo(1)
    }

    @Test
    fun `ducking lowers the volume without stopping or losing the master`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.volume = 0.5f

        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        assertThat(tracks["rain"]!!.paused).isFalse()
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.1f)  // 1.0 * 0.5 * 0.2
    }

    @Test
    fun `regaining focus after ducking restores the exact master volume`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.volume = 0.5f
        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        focus.emit(FocusChange.GAINED)

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `a sound added while ducked comes in ducked too`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `stopping abandons the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.stop()

        assertThat(focus.abandons).isEqualTo(1)
    }

    @Test
    fun `pausing does not abandon the focus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.pause()

        assertThat(focus.abandons).isEqualTo(0)
    }
}
