package com.jbgsoft.ambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Two methods, deliberately, because a single one could not do this.
 *
 * Under Android Test Orchestrator each test method runs in its own process, so the
 * app is genuinely torn down between the two halves and the second has to rebuild
 * the mix from DataStore. Written as one method it would have had to kill the app
 * itself — and instrumentation lives inside the app's process, so that kills the test.
 *
 * Phase 3b's persistence fix is covered by JVM tests over the repository; nothing
 * until now checked that the whole path — DataStore, repository, service and UI —
 * actually reassembles a five-sound mix after the process dies.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class MixPersistenceTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun step1_activateAllFiveSounds() {
        MixerUi.launchApp()
        MixerUi.activateAllSounds()

        // activeSoundCount() only reads while the picker sheet is open, and
        // activateAllSounds() always leaves it closed (see MixerUi's own doc comment) -
        // reopen it the same way MixPlaybackTest.allFiveSoundsPlayAtOnce() does, so this
        // assertion has something to observe instead of reading the home screen's zero.
        device.wait(Until.findObject(By.text("Change")), 10_000)?.click()
        device.waitForIdle()
        assertThat(MixerUi.activeSoundCount()).isEqualTo(5)
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun step2_theMixIsRebuiltInAFreshProcess() {
        MixerUi.launchApp()
        MixerUi.pressPlay()

        assertThat(AudioState.awaitStartedTracks(expected = 5)).isEqualTo(5)
    }
}
