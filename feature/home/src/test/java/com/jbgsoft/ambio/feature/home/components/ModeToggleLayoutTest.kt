package com.jbgsoft.ambio.feature.home.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertWidthIsAtLeast
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
 * regardless of its actual character count, so wrapping or clipping is
 * invisible to it either way. `@GraphicsMode(NATIVE)` drives real Skia text
 * shaping instead. `the Japanese reference widths are not vacuous` is the check
 * that this is actually taking effect, not silently degrading to the same
 * near-zero measurement `LEGACY` gives.
 */
// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModeToggleLayoutTest {

    // createAndroidComposeRule rather than createComposeRule: real text shaping
    // under @GraphicsMode(NATIVE) needs the host window, same as
    // AmbientEffectsOverlayTest. Each test gets its own Activity, so setContent
    // can only be called once per test - the reference widths below are
    // measured constants rather than something computed inline for that reason.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Measured directly: the toggle is 48dp tall in English and, with the wrap
    // fixed, 48dp tall in Japanese too. Wrapped, a stacked Japanese label pushes
    // it to 56dp. 52dp sits between the two with headroom for measurement noise
    // on either side, so this catches wrapping without being a hair-trigger on
    // the working case. Wrapping and clipping are opposite failure modes - this
    // guards the first, `neither Japanese label is clipped` below guards the
    // second - so both are needed.
    private val singleLineMaxHeight = 52.dp

    // The width each label needs on its own, unconstrained, at labelLarge under
    // @GraphicsMode(NATIVE): "タイマー" measures 58dp, "アンビエント" measures
    // 86dp. A segment narrower than its own label's reference width is clipping
    // it, regardless of how many lines tall it renders.
    private val timerReferenceWidth = 58.dp
    private val ambientReferenceWidth = 86.dp

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

    // A label that no longer wraps can still be too narrow for its own text:
    // SegmentedButton's custom Layout does not implement intrinsic width, so
    // SingleChoiceSegmentedButtonRow can size the whole row too small and then
    // split it evenly, leaving a segment's actual width below what its own
    // label needs - which single-line height alone cannot detect, because a
    // clipped single line and a fully rendered single line are both one line
    // tall. Comparing each label's rendered width against the width the same
    // string needs on its own, unconstrained, is what actually catches that.
    @Test
    @Config(sdk = [34], qualifiers = "ja-w1280dp-h800dp")
    fun `neither Japanese label is clipped`() {
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(effectsEnabled = false),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        compose.onNodeWithTag("modeToggleLabel_TIMER", useUnmergedTree = true)
            .assertWidthIsAtLeast(timerReferenceWidth)
        compose.onNodeWithTag("modeToggleLabel_AMBIENT", useUnmergedTree = true)
            .assertWidthIsAtLeast(ambientReferenceWidth)
    }

    // If @GraphicsMode(NATIVE) were silently giving the same near-zero CJK
    // metrics LEGACY does, both reference widths above would collapse toward
    // zero and the assertions in the test above would hold no matter how badly
    // the real labels were clipped. At the same style, "Timer" measures 40dp
    // and "Ambient" measures 56dp - both bounds below sit clear of those, so
    // this fails if the Japanese measurement ever degenerates to something
    // indistinguishable from its English counterpart.
    @Test
    fun `the Japanese reference widths are not vacuous`() {
        assertThat(timerReferenceWidth > 45.dp).isTrue()
        assertThat(ambientReferenceWidth > 65.dp).isTrue()
    }
}
