package com.jbgsoft.ambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The regression test for the bug that made this work necessary: the app built five
 * ExoPlayers and only one of them was audible, because each requested audio focus for
 * itself and the newest evicted the rest.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MixPlaybackTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun startFresh() {
        // No MixerUi.clearAppData() here: `pm clear` force-stops the app package, and
        // this module self-instruments (androidTest has no separate applicationId), so
        // the instrumentation runs *inside* com.jbgsoft.ambio's own process. Clearing app
        // data would kill the test host mid-test - the same defect that already broke
        // LaunchTest on this branch. launchApp() alone is enough: without a pristine
        // state, this test must tolerate whatever a previous run left behind, and its
        // helpers are built for that - activateAllSounds() leaves an already-active sound
        // alone, and pressPlay() only clicks when a "Play" affordance is actually present,
        // so a mix already playing from a prior run is left playing rather than paused.
        MixerUi.launchApp()
    }

    @Test
    fun allFiveSoundsPlayAtOnce() {
        MixerUi.activateAllSounds()

        // MixerUi.activateAllSounds() always leaves the picker sheet closed (see its own
        // doc comment: "...and closes it"). MixerUi.activeSoundCount() reads the "Remove
        // X from the mix" description that SoundCard only carries while composed inside
        // SoundBottomSheet - the home screen's own summary (CurrentSoundBar) shows a
        // joined-names label instead, with no per-sound affordance. So activeSoundCount()
        // can only ever report a real number while that sheet is open; called against the
        // home screen it is always 0, regardless of the actual mix. Reopening the sheet
        // the same way activateAllSounds() itself does (tap "Change"), reading the count,
        // then closing it the same way (system back) puts the assertion the brief calls
        // for - catching a picker regression before it is misread as an audio-focus one -
        // somewhere it can actually observe the mix.
        device.wait(Until.findObject(By.text("Change")), 10_000)?.click()
        device.waitForIdle()
        assertThat(MixerUi.activeSoundCount()).isEqualTo(5)
        device.pressBack()
        device.waitForIdle()

        MixerUi.pressPlay()

        assertThat(AudioState.awaitStartedTracks(expected = 5)).isEqualTo(5)
    }
}
