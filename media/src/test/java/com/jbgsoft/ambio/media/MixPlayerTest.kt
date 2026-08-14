package com.jbgsoft.ambio.media

import android.os.Looper
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    private var emptyPlayRequests = 0

    private fun player(): MixPlayer =
        MixPlayer(
            Looper.getMainLooper(),
            { id -> FakeTrack().also { tracks[id] = it } },
            focus,
            { emptyPlayRequests++ }
        )

    /**
     * Robolectric's looper is PAUSED by default, so posted work only runs when the
     * looper is idled. This is what lets the ramps be tested without real time.
     */
    private fun advance(ms: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Long enough for any ramp to reach its target, with a tick to spare. */
    private fun settle() = advance(MixPlayer.FADE_IN_MS + MixPlayer.TICK_MS)

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
    fun `a track's volume is its level times the master times the bus`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.setSoundLevel("ocean", 0.5f)
        mix.volume = 0.4f

        // 1.0 * 0.4 * 0.70710678, and 0.5 * 0.4 * 0.70710678
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.28284273f)
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.001f).of(0.14142136f)
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
        // 0.25 * 0.70710678, and 0.5 * 0.70710678
        assertThat(tracks["forest"]!!.appliedVolume).isWithin(0.001f).of(0.17677669f)
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.35355339f)
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
    fun `a user pause during a transient loss survives regaining focus`() {
        // The pair to the test above, and the one that actually pins the reset: there,
        // pausedByFocusLoss is false the whole way through, so the mix would stay paused
        // even if nothing ever cleared the flag. Here the transient loss sets it first,
        // so only an explicit reset on the user's pause keeps GAINED from overriding a
        // decision the user made after the interruption started.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        focus.emit(FocusChange.LOST_TRANSIENT)

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
        settle()

        // 1.0 * 0.70710678 * 0.2
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.001f).of(0.14142136f)
    }

    @Test
    fun `pressing play after a permanent loss while ducked restores the master volume`() {
        // GAINED is not a reachable exit from this state: a permanent loss abandons the
        // focus, so the system has nothing left to hand back. Pressing play is the only
        // way out, and it has to undo the duck itself or the mix is quiet for good.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.volume = 0.5f
        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)
        focus.emit(FocusChange.LOST)

        mix.play()

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.5f)
        assertThat(tracks["rain"]!!.paused).isFalse()
    }

    @Test
    fun `stopping clears the duck so a rebuilt mix does not come back quiet`() {
        // stop() releases every track and abandons the focus, so the duck it was holding
        // no longer refers to anything. setSoundActive() applies the multiplier at
        // creation time, so a stale one silently quiets tracks that never existed when
        // the ducking app was playing.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)

        mix.stop()
        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(1f)
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

    // --- refusing to play an empty mix ---

    @Test
    fun `playing with no sounds does not request audio focus`() {
        val mix = player()

        mix.play()

        assertThat(focus.requests).isEqualTo(0)
    }

    @Test
    fun `playing with no sounds tells the service instead`() {
        val mix = player()

        mix.play()

        assertThat(emptyPlayRequests).isEqualTo(1)
    }

    @Test
    fun `playing with no sounds leaves the player paused`() {
        val mix = player()

        mix.play()

        assertThat(mix.playWhenReady).isFalse()
    }

    @Test
    fun `playing with sounds present still works and does not call back`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)

        mix.play()

        assertThat(mix.playWhenReady).isTrue()
        assertThat(focus.requests).isEqualTo(1)
        assertThat(emptyPlayRequests).isEqualTo(0)
        assertThat(tracks["rain"]!!.paused).isFalse()
    }

    @Test
    fun `pausing with no sounds does not call back`() {
        val mix = player()

        mix.pause()

        assertThat(emptyPlayRequests).isEqualTo(0)
    }

    // --- per-sound fades ---

    @Test
    fun `a sound added while playing fades in instead of cutting to full volume`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]!!.appliedVolume).isEqualTo(0f)
        advance(MixPlayer.FADE_IN_MS / 2)
        val half = tracks["ocean"]!!.appliedVolume
        assertThat(half).isGreaterThan(0.3f)
        assertThat(half).isLessThan(0.7f)
        settle()
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.01f).of(0.70710678f)
    }

    @Test
    fun `a sound added while paused is at its full level the moment play starts`() {
        // Choosing sounds before pressing play must not cost a fade each: the master
        // fade in AudioServiceConnection already covers the start of playback.
        val mix = player()

        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(1f)
    }

    @Test
    fun `no ramp runs while the mix is paused`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.pause()

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        // Settled immediately, not over 1500ms, because nothing is audible anyway.
        // 1.0 * 0.70710678, the two-sound bus -- the point is that it is settled, not
        // that it is 1.0.
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.001f).of(0.70710678f)
    }

    @Test
    fun `ducking during a fade-in composes with the ramp rather than replacing it`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        advance(MixPlayer.FADE_IN_MS / 2)

        focus.emit(FocusChange.LOST_TRANSIENT_DUCK)
        val ducked = tracks["ocean"]!!.appliedVolume

        // Partway through the ramp AND ducked: the product of both, not either alone.
        assertThat(ducked).isGreaterThan(0f)
        assertThat(ducked).isLessThan(0.2f)
        settle()
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.01f).of(0.14142136f)
    }

    @Test
    fun `changing a level mid-fade does not jump the sound to its new level`() {
        // The volume product has one definition. A caller that recomputes it by hand
        // skips the ramp, which is the cut this fade exists to remove.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.setSoundLevel("ocean", 1f)

        assertThat(tracks["ocean"]!!.appliedVolume).isLessThan(0.1f)
        settle()
        // 1.0 * 0.70710678, the two-sound bus.
        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.01f).of(0.70710678f)
    }

    // --- fade-out on removal ---

    @Test
    fun `removing a sound releases its track only after the fade finishes`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()

        mix.setSoundActive("ocean", audioRes = 2, active = false)

        // Still alive and still audible, mid-fade.
        assertThat(tracks["ocean"]!!.released).isFalse()
        advance(MixPlayer.FADE_OUT_MS / 2)
        assertThat(tracks["ocean"]!!.released).isFalse()
        assertThat(tracks["ocean"]!!.appliedVolume).isGreaterThan(0f)

        advance(MixPlayer.FADE_OUT_MS)
        assertThat(tracks["ocean"]!!.released).isTrue()
    }

    @Test
    fun `a redundant play call does not cut an in-flight fade-out`() {
        // Reachable from a car or Bluetooth head unit re-sending PLAY while already
        // playing, and from startPlayback() during the 3-second stop fade. Only a
        // genuine paused->playing transition may settle ramps; a PLAY that changes
        // nothing must leave a retiring track exactly where its fade left it.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        mix.setSoundActive("ocean", audioRes = 2, active = false)
        advance(MixPlayer.FADE_OUT_MS / 2)
        val midFade = tracks["ocean"]!!.appliedVolume
        assertThat(tracks["ocean"]!!.released).isFalse()
        assertThat(midFade).isGreaterThan(0f)

        mix.play()

        // Still fading, not cut to silence or released by the redundant play().
        assertThat(tracks["ocean"]!!.released).isFalse()
        assertThat(tracks["ocean"]!!.appliedVolume).isGreaterThan(0f)

        advance(MixPlayer.FADE_OUT_MS)
        assertThat(tracks["ocean"]!!.released).isTrue()
    }

    @Test
    fun `a retiring sound leaves the mix state immediately`() {
        // The fade is audio cleanup. The UI and the empty-mix guard must see the
        // removal at once, or the player looks busy while nothing is playing.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.setSoundActive("rain", audioRes = 1, active = false)

        assertThat(mix.playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `re-adding a sound mid-fade reuses its track instead of starting a second one`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        val original = tracks["ocean"]
        mix.setSoundActive("ocean", audioRes = 2, active = false)
        advance(MixPlayer.FADE_OUT_MS / 2)

        mix.setSoundActive("ocean", audioRes = 2, active = true)
        settle()

        assertThat(tracks["ocean"]).isSameInstanceAs(original)
        assertThat(original!!.released).isFalse()
        assertThat(original.appliedVolume).isGreaterThan(0f)
    }

    @Test
    fun `removing a sound while paused releases it at once without a fade`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        mix.pause()

        mix.setSoundActive("ocean", audioRes = 2, active = false)

        assertThat(tracks["ocean"]!!.released).isTrue()
    }

    @Test
    fun `pausing mid-fade releases the retiring track rather than stranding it`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        mix.setSoundActive("ocean", audioRes = 2, active = false)

        mix.pause()

        assertThat(tracks["ocean"]!!.released).isTrue()
    }

    @Test
    fun `stopping mid-fade releases the retiring track immediately`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()
        mix.setSoundActive("ocean", audioRes = 2, active = false)

        mix.stop()

        assertThat(tracks.values.all { it.released }).isTrue()
    }

    @Test
    fun `setMix fades out sounds it drops rather than cutting them`() {
        val mix = player()
        mix.setMix(
            listOf(MixEntry("rain", 1, 1f), MixEntry("ocean", 2, 1f)),
            title = "Rain + Ocean"
        )
        mix.play()

        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")

        assertThat(tracks["ocean"]!!.released).isFalse()
        advance(MixPlayer.FADE_OUT_MS + MixPlayer.TICK_MS)
        assertThat(tracks["ocean"]!!.released).isTrue()
    }

    // --- the mix bus ---

    @Test
    fun `the bus holds the mix level as sounds are added`() {
        // Uncorrelated ambience sums by power, so 1/sqrt(N) keeps total loudness
        // constant: adding a sound changes the texture, not the volume.
        val mix = player()

        mix.setSoundActive("rain", audioRes = 1, active = true)
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(1f)

        mix.setSoundActive("ocean", audioRes = 2, active = true)
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.70710678f)

        mix.setSoundActive("forest", audioRes = 3, active = true)
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.57735026f)
    }

    @Test
    fun `the bus rises again when a sound leaves`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)

        mix.setSoundActive("ocean", audioRes = 2, active = false)

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(1f)
    }

    @Test
    fun `a bus change is ramped, not stepped`() {
        // Without this the normalisation introduces exactly the click the fades
        // were added to remove.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.play()

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        // Still near 1.0 immediately after, not already down at 0.707.
        assertThat(tracks["rain"]!!.appliedVolume).isGreaterThan(0.95f)
        settle()
        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(0.70710678f)
    }

    @Test
    fun `an emptied mix leaves the bus at unity for the next sound`() {
        // 1/sqrt(0) is infinity; the next sound to arrive must not inherit it.
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("rain", audioRes = 1, active = false)

        mix.setSoundActive("ocean", audioRes = 2, active = true)

        assertThat(tracks["ocean"]!!.appliedVolume).isWithin(0.001f).of(1f)
    }

    @Test
    fun `stopping resets the bus so a rebuilt mix is not attenuated`() {
        val mix = player()
        mix.setSoundActive("rain", audioRes = 1, active = true)
        mix.setSoundActive("ocean", audioRes = 2, active = true)
        mix.play()

        mix.stop()
        mix.setMix(listOf(MixEntry("rain", 1, 1f)), title = "Rain")

        assertThat(tracks["rain"]!!.appliedVolume).isWithin(0.001f).of(1f)
    }
}
