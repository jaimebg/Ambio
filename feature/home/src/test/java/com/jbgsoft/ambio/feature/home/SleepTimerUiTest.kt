package com.jbgsoft.ambio.feature.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.feature.home.components.TimerDisplay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ambient mode's sleep timer, as the screen shows it: the dial counts it down
 * where it used to show the infinity sign, and the row under the dial offers
 * the durations where Timer mode offers the focus presets.
 */
// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the dial counts the sleep timer down in ambient mode`() {
        compose.setContent {
            TimerDisplay(
                timerState = TimerState.Running(remainingMs = 12 * 60_000L + 5_000L, totalMs = 30 * 60_000L),
                mode = AppMode.AMBIENT,
                isPlaying = true,
                selectedMinutes = 25
            )
        }

        compose.onNodeWithText("12:05").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.state_sleep_timer)).assertIsDisplayed()
        compose.onNodeWithText("∞").assertDoesNotExist()
    }

    @Test
    fun `a paused sleep timer says so on the dial`() {
        compose.setContent {
            TimerDisplay(
                timerState = TimerState.Paused(remainingMs = 9 * 60_000L, totalMs = 30 * 60_000L),
                mode = AppMode.AMBIENT,
                isPlaying = false,
                selectedMinutes = 25
            )
        }

        compose.onNodeWithText("09:00").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.state_paused)).assertIsDisplayed()
    }

    @Test
    fun `the dial keeps the infinity sign in ambient mode with no sleep timer running`() {
        compose.setContent {
            TimerDisplay(
                timerState = TimerState.Idle,
                mode = AppMode.AMBIENT,
                isPlaying = true,
                selectedMinutes = 25
            )
        }

        compose.onNodeWithText("∞").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.state_ambient_mode)).assertIsDisplayed()
    }

    // The phone window the transport test uses: wide enough for the chip row,
    // tall enough that the row under the dial is on screen without scrolling.
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h866dp")
    fun `ambient mode offers the sleep durations where timer mode offers the presets`() {
        val events = mutableListOf<HomeEvent>()
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(mode = AppMode.AMBIENT, sleepMinutes = 15, effectsEnabled = false),
                onEvent = { events += it },
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        compose.onNodeWithText(context.getString(R.string.label_sleep_timer)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.label_focus_duration)).assertDoesNotExist()

        compose.onNodeWithText("30 min").performClick()
        assertThat(events).contains(HomeEvent.SetSleepMinutes(30))

        compose.onNodeWithText(context.getString(R.string.sleep_timer_off)).performClick()
        assertThat(events).contains(HomeEvent.SetSleepMinutes(0))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h866dp")
    fun `timer mode does not offer the sleep durations`() {
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(mode = AppMode.TIMER, effectsEnabled = false),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        compose.onNodeWithText(context.getString(R.string.label_sleep_timer)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.label_focus_duration)).assertIsDisplayed()
    }
}
