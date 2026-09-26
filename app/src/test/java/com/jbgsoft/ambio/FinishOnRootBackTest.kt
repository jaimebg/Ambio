package com.jbgsoft.ambio

import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinishOnRootBackTest {

    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun `Back on the last screen finishes the activity`() {
        activity.finishOnRootBack()

        // Asserted before pressing: Robolectric finishes a root activity on Back by itself,
        // which a launcher-started task on a real device since Android 12 does not (#9).
        // Only an enabled callback of our own keeps the system's moveTaskToBack out of it.
        assertThat(activity.onBackPressedDispatcher.hasEnabledCallbacks()).isTrue()

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a callback registered later, like NavHost's, still takes Back first`() {
        activity.finishOnRootBack()
        var popped = false
        activity.onBackPressedDispatcher.addCallback(activity) { popped = true }

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(popped).isTrue()
        assertThat(activity.isFinishing).isFalse()
    }
}
