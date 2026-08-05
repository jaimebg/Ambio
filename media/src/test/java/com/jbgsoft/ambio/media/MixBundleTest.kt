package com.jbgsoft.ambio.media

import android.os.Bundle
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SET_MIX crosses a process boundary, so the service has to treat the bundle as
 * untrusted. The interesting cases are all failure ones: an empty mix is a legitimate
 * "release everything", so anything that decodes to empty would silence the app. These
 * tests pin that a bundle the service cannot read is refused rather than obeyed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MixBundleTest {

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
        override fun request(): Boolean = true
        override fun abandon() {}
        override fun onChange(listener: (FocusChange) -> Unit) {}
    }

    private val tracks = mutableMapOf<String, FakeTrack>()

    private fun player(): MixPlayer =
        MixPlayer(Looper.getMainLooper(), { id -> FakeTrack().also { tracks[id] = it } }, FakeFocus(), {})

    private val rain = MixEntry("rain", 42, 0.75f)
    private val ocean = MixEntry("ocean", 7, 0.5f)

    // --- round trip ---

    @Test
    fun `a round trip preserves every entry and the title`() {
        val bundle = MixBundle.encode(listOf(rain, ocean), "Rain + Ocean")

        assertThat(MixBundle.decode(bundle)).containsExactly(rain, ocean).inOrder()
        assertThat(MixBundle.decodeTitle(bundle)).isEqualTo("Rain + Ocean")
    }

    // --- refusals ---

    @Test
    fun `a bundle missing the levels array is refused`() {
        val bundle = MixBundle.encode(listOf(rain), "Rain")
        bundle.remove(MixCommands.ARG_LEVELS)

        assertThat(MixBundle.decode(bundle)).isNull()
    }

    @Test
    fun `a bundle missing the audio resources is refused`() {
        val bundle = MixBundle.encode(listOf(rain), "Rain")
        bundle.remove(MixCommands.ARG_AUDIO_RES)

        assertThat(MixBundle.decode(bundle)).isNull()
    }

    @Test
    fun `arrays of differing lengths are refused rather than truncated`() {
        val bundle = MixBundle.encode(listOf(rain, ocean), "Rain + Ocean")
        bundle.putFloatArray(MixCommands.ARG_LEVELS, floatArrayOf(1f))

        assertThat(MixBundle.decode(bundle)).isNull()
    }

    @Test
    fun `an empty bundle is refused`() {
        assertThat(MixBundle.decode(Bundle())).isNull()
    }

    @Test
    fun `an empty mix is refused`() {
        // The repository never produces one, so receiving it means something is wrong —
        // and obeying it would release every track.
        assertThat(MixBundle.decode(MixBundle.encode(emptyList(), "Nothing"))).isNull()
    }

    // --- the symptom the refusal exists to prevent ---

    @Test
    fun `a malformed bundle leaves the running mix untouched`() {
        val mix = player()
        assertThat(applySetMix(mix, MixBundle.encode(listOf(rain, ocean), "Rain + Ocean")))
            .isTrue()

        val malformed = Bundle().apply {
            putStringArray(MixCommands.ARG_SOUND_IDS, arrayOf("rain", "ocean"))
            // audio resources and levels never made it across
        }
        val accepted = applySetMix(mix, malformed)

        assertThat(accepted).isFalse()
        assertThat(tracks["rain"]!!.released).isFalse()
        assertThat(tracks["ocean"]!!.released).isFalse()
        assertThat(mix.mediaMetadata.title.toString()).isEqualTo("Rain + Ocean")
    }

    @Test
    fun `a well formed bundle is applied to the player`() {
        val mix = player()

        val accepted = applySetMix(mix, MixBundle.encode(listOf(rain), "Rain"))

        assertThat(accepted).isTrue()
        assertThat(tracks["rain"]!!.startedWith).isEqualTo(42)
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.75f)
        assertThat(mix.mediaMetadata.title.toString()).isEqualTo("Rain")
    }
}
