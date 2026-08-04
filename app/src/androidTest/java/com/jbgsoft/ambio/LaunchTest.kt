package com.jbgsoft.ambio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cheapest test here, and not a formality: in Phase 2 this app crashed on launch
 * for two entire tasks with CI green throughout, because nothing ever ran it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LaunchTest {

    @Test
    fun theAppStartsAndStaysUp() {
        // No MixerUi.forceStop() here: this module self-instruments (androidTest has no
        // separate applicationId), so the instrumentation runs *inside* com.jbgsoft.ambio's
        // own process. Force-stopping that package from within it kills the instrumentation
        // host mid-test - confirmed via logcat (ActivityManager: Killing ...: from pid <shell>,
        // ~40ms after the test starts) - which Gradle reports as "Instrumentation run failed
        // due to Process crashed" rather than as a test failure. launchApp() alone is enough:
        // there is nothing to force-stop before the very first launch of a fresh instrumentation.
        MixerUi.launchApp()
        Thread.sleep(3_000)

        assertThat(MixerUi.isRunning()).isTrue()
        assertThat(AudioState.shell("logcat -d -b crash").contains("FATAL EXCEPTION")).isFalse()
    }
}
