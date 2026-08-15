package com.jbgsoft.ambio.feature.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RtlLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Real Arabic (not the ar-XB pseudolocale, which needs a build-config flag
    // this project does not set), ldrtl forcing right-to-left layout direction,
    // and a width past the 840dp breakpoint so the two-pane path is what gets
    // mirrored.
    @Test
    @Config(sdk = [34], qualifiers = "ar-ldrtl-w1280dp-h800dp")
    fun `the two-pane layout renders in Arabic without losing either pane`() {
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(effectsEnabled = false),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        // Both panes must still be present when the layout mirrors: the picker
        // title from the right pane, and the settings icon from the left pane's
        // header. Asserting only the picker would pass even if the left pane
        // vanished entirely.
        compose.onNodeWithText(context.getString(R.string.sound_picker_title))
            .assertIsDisplayed()

        compose.onNodeWithContentDescription(
            context.getString(R.string.action_open_settings)
        ).assertIsDisplayed()
    }
}
