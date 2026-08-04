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

    private val tracks = mutableMapOf<String, FakeTrack>()

    private fun player(): MixPlayer =
        MixPlayer(Looper.getMainLooper()) { id -> FakeTrack().also { tracks[id] = it } }

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
}
