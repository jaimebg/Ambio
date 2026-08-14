package com.jbgsoft.ambio.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.feature.home.test.R as TestR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val rain = Sound(
        id = "rain",
        nameRes = TestR.string.test_sound_name,
        icon = Icons.Default.WaterDrop,
        audioRes = 0,
        theme = SoundTheme.RAIN,
        glow = SoundGlow.RAIN
    )

    private val state = HomeUiState(
        activeMix = listOf(ActiveSound(rain, 1f)),
        availableSounds = listOf(rain),
        effectsEnabled = false
    )

    // Every test below declares its window twice, and both halves are load-bearing.
    // Modifier.size cannot grow past the constraints it is handed and the default
    // Robolectric device is 320x470dp, so the size the test asks for only becomes
    // real if the device qualifiers grant it. Drop the qualifiers and the box is
    // silently clamped back to 320x470 — which still passes some of these
    // assertions, while measuring a screen nobody wrote a test for.
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `the stateless body renders from plain state with no view model`() {
        compose.setContent {
            Box(Modifier.size(411.dp, 891.dp)) {
                HomeScreen(
                    uiState = state,
                    onEvent = {},
                    onNavigateToSettings = {},
                    onNavigateToStats = {}
                )
            }
        }

        compose.onNodeWithContentDescription(
            context.getString(R.string.action_open_settings)
        ).assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `tapping the settings icon reports navigation, not an event`() {
        var navigated = false
        compose.setContent {
            Box(Modifier.size(411.dp, 891.dp)) {
                HomeScreen(
                    uiState = state,
                    onEvent = {},
                    onNavigateToSettings = { navigated = true },
                    onNavigateToStats = {}
                )
            }
        }

        compose.onNodeWithContentDescription(
            context.getString(R.string.action_open_settings)
        ).performClick()

        assertThat(navigated).isTrue()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `at expanded width the picker is visible without opening the sheet`() {
        compose.setContent {
            Box(Modifier.size(1280.dp, 800.dp)) {
                HomeScreen(
                    // showSoundPicker stays false: the pane must not depend on it.
                    uiState = state.copy(showSoundPicker = false),
                    onEvent = {},
                    onNavigateToSettings = {},
                    onNavigateToStats = {}
                )
            }
        }

        compose.onNodeWithText(
            context.getString(R.string.sound_picker_title)
        ).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `at compact width the picker stays hidden until the sheet is asked for`() {
        compose.setContent {
            Box(Modifier.size(411.dp, 891.dp)) {
                HomeScreen(
                    uiState = state.copy(showSoundPicker = false),
                    onEvent = {},
                    onNavigateToSettings = {},
                    onNavigateToStats = {}
                )
            }
        }

        compose.onNodeWithText(
            context.getString(R.string.sound_picker_title)
        ).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `at expanded width the change button is gone because the picker is already open`() {
        compose.setContent {
            Box(Modifier.size(1280.dp, 800.dp)) {
                HomeScreen(
                    uiState = state,
                    onEvent = {},
                    onNavigateToSettings = {},
                    onNavigateToStats = {}
                )
            }
        }

        // The settings icon sits in the timer column's header row, so this is the
        // proof that the left pane rendered at all. Without it the assertion below
        // would also pass on a layout that dropped the timer column entirely.
        compose.onNodeWithContentDescription(
            context.getString(R.string.action_open_settings)
        ).assertIsDisplayed()

        compose.onNodeWithText(
            context.getString(R.string.action_change_sound)
        ).assertDoesNotExist()
    }
}
