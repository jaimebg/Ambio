package com.jbgsoft.ambio.feature.home.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.feature.home.HomeScreen
import com.jbgsoft.ambio.feature.home.HomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric's default (`LEGACY`) graphics shadow does not carry real CJK glyph
 * metrics: a Japanese label measured under it comes back only a few dp wide
 * regardless of its actual character count, so it never wraps and this bug is
 * invisible to it. `@GraphicsMode(NATIVE)` drives real Skia text shaping instead,
 * which reproduces the wrap this test exists to catch — confirmed by measuring
 * the same label at 60dp width and finding it fills the width and wraps to two
 * lines under `NATIVE`, versus collapsing to ~6dp under `LEGACY`.
 */
// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModeToggleLayoutTest {

    // createAndroidComposeRule rather than createComposeRule: real text shaping
    // under @GraphicsMode(NATIVE) needs the host window, same as
    // AmbientEffectsOverlayTest.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Measured directly: the toggle is 48dp tall in English and, with the fix in
    // place, 48dp tall in Japanese too. Unfixed, a wrapped Japanese label pushes
    // it to 56dp. 52dp sits between the two with headroom for measurement noise
    // on either side, so this catches any wrap without being a hair-trigger on
    // the working case.
    private val singleLineMaxHeight = 52.dp

    @Test
    @Config(sdk = [34], qualifiers = "ja-w1280dp-h800dp")
    fun `the mode toggle stays one line in Japanese`() {
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(effectsEnabled = false),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        val height = compose.onNodeWithTag("modeToggle").getUnclippedBoundsInRoot().height
        assertThat(height <= singleLineMaxHeight).isTrue()
    }

    // Companion to the test above: a bound that also failed in English would be
    // measuring window chrome or padding, not the wrap this test targets.
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `the mode toggle stays one line at the default qualifier`() {
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(effectsEnabled = false),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        val height = compose.onNodeWithTag("modeToggle").getUnclippedBoundsInRoot().height
        assertThat(height <= singleLineMaxHeight).isTrue()
    }
}
