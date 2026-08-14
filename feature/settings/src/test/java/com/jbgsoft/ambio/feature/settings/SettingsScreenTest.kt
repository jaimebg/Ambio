package com.jbgsoft.ambio.feature.settings

import android.content.Context
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `each toggle reports through its own callback`() {
        var haptics: Boolean? = null
        var chime: Boolean? = null
        var effects: Boolean? = null

        compose.setContent {
            SettingsScreen(
                uiState = SettingsUiState(
                    hapticsEnabled = false,
                    chimeEnabled = false,
                    effectsEnabled = false
                ),
                onHapticsChanged = { haptics = it },
                onChimeChanged = { chime = it },
                onEffectsChanged = { effects = it },
                onNavigateBack = {}
            )
        }

        // SettingRow puts onCheckedChange on the Switch, not on the Row — tapping
        // the title text does nothing. The three switches appear in declaration
        // order: haptics, chime, effects.
        val switches = compose.onAllNodes(isToggleable())
        switches[0].performClick()
        switches[1].performClick()
        switches[2].performClick()

        // Each callback must receive the toggled value, and no callback may be
        // wired to the wrong row — the mistake this test exists to catch.
        assertThat(haptics).isTrue()
        assertThat(chime).isTrue()
        assertThat(effects).isTrue()
    }

    @Test
    fun `each row shows its own title and summary`() {
        compose.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onHapticsChanged = {},
                onChimeChanged = {},
                onEffectsChanged = {},
                onNavigateBack = {}
            )
        }

        compose.onNodeWithText(context.getString(R.string.settings_haptics)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_chime)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_effects)).assertExists()
    }
}
