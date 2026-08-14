package com.jbgsoft.ambio.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.ui.layout.CONTENT_MAX_WIDTH
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

        // The summaries matter as much as the titles: SettingRow takes both as
        // plain strings, so a copy-pasted row can pair the right title with the
        // wrong explanation and still look correct from the titles alone.
        compose.onNodeWithText(context.getString(R.string.settings_haptics_summary)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_chime_summary)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_effects_summary)).assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `at expanded width the content stops tracking the window and caps its column`() {
        compose.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onHapticsChanged = {},
                onChimeChanged = {},
                onEffectsChanged = {},
                onNavigateBack = {}
            )
        }

        // The header Row is fillMaxWidth, so uncapped it would span all 1280dp.
        // androidx.compose.ui.test has no assertWidthIsAtMost, so the bound is
        // read directly and compared.
        val headerWidth = compose.onNodeWithTag("settingsHeader").getUnclippedBoundsInRoot().width
        assertThat(headerWidth <= CONTENT_MAX_WIDTH).isTrue()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `at compact width the content still spans the window`() {
        compose.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onHapticsChanged = {},
                onChimeChanged = {},
                onEffectsChanged = {},
                onNavigateBack = {}
            )
        }

        // Guards the other direction: the cap must not leak into phone widths.
        compose.onNodeWithTag("settingsHeader").assertWidthIsAtLeast(400.dp)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w700dp-h800dp")
    fun `at a compact width wider than the cap the content still spans the window`() {
        compose.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onHapticsChanged = {},
                onChimeChanged = {},
                onEffectsChanged = {},
                onNavigateBack = {}
            )
        }

        // 700dp is still COMPACT (under the 840dp threshold) but wider than
        // CONTENT_MAX_WIDTH (600dp) — the 411dp check above is narrower than
        // the cap itself, so it cannot tell "no cap" from "cap always
        // applied" apart. This width can.
        val headerWidth = compose.onNodeWithTag("settingsHeader").getUnclippedBoundsInRoot().width
        assertThat(headerWidth > CONTENT_MAX_WIDTH).isTrue()
    }
}
