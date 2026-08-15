package com.jbgsoft.ambio.feature.home.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.feature.home.HomeScreen
import com.jbgsoft.ambio.feature.home.HomeUiState
import com.jbgsoft.ambio.feature.home.R
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
 * shaping instead, and `Japanese shapes wider than English` is the check that it
 * is actually taking effect rather than silently degrading — every other test
 * here measures its own reference widths through the same engine it measures the
 * rendered labels with, so a collapse would take both sides down together and go
 * unnoticed.
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
    // can only be called once per test.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Measured directly: the toggle is 48dp tall in English and, with the wrap
    // fixed, 48dp tall in Japanese too. Wrapped, a stacked Japanese label pushes
    // it to 56dp. 52dp sits between the two with headroom for measurement noise
    // on either side, so this catches wrapping without being a hair-trigger on
    // the working case. Wrapping and clipping are opposite failure modes - this
    // guards the first, the clipping tests below guard the second - so both are
    // needed.
    private val singleLineMaxHeight = 52.dp

    /**
     * The rendered toggle, plus a ruler that measures any string at exactly the
     * style and density the toggle's own labels are laid out with.
     *
     * The reference widths come from this ruler rather than from constants
     * because constants would go stale in silence: re-wording a `mode_*` string
     * or retuning the type scale invalidates a hard-coded dp in one direction
     * and produces a confusing spurious failure in the other.
     */
    private class Toggle(
        val timerLabel: String,
        val ambientLabel: String,
        val widthOf: (String) -> Dp
    )

    private fun showHome(mode: AppMode = AppMode.TIMER): Toggle {
        lateinit var timerLabel: String
        lateinit var ambientLabel: String
        lateinit var measurer: TextMeasurer
        lateinit var style: TextStyle
        lateinit var density: Density

        compose.setContent {
            timerLabel = stringResource(R.string.mode_timer)
            ambientLabel = stringResource(R.string.mode_ambient)
            measurer = rememberTextMeasurer()
            // ModeToggle sizes its segments off labelLarge and nothing between
            // here and it installs a theme, so this is the style the labels
            // themselves are shaped with.
            style = MaterialTheme.typography.labelLarge
            density = LocalDensity.current

            HomeScreen(
                uiState = HomeUiState(effectsEnabled = false, mode = mode),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        return Toggle(timerLabel, ambientLabel) { text ->
            with(density) { measurer.measure(text, style).size.width.toDp() }
        }
    }

    private fun renderedWidthOf(tag: String): Dp =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().width

    private fun textLayoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.first()
    }

    @Test
    @Config(sdk = [34], qualifiers = "ja-w1280dp-h800dp")
    fun `the mode toggle stays one line in Japanese`() {
        showHome()

        val height = compose.onNodeWithTag("modeToggle").getUnclippedBoundsInRoot().height
        assertThat(height <= singleLineMaxHeight).isTrue()
    }

    // Companion to the test above: a bound that also failed in English would be
    // measuring window chrome or padding, not the wrap this test targets.
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `the mode toggle stays one line at the default qualifier`() {
        showHome()

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
        val toggle = showHome()

        compose.onNodeWithTag("modeToggleLabel_TIMER", useUnmergedTree = true)
            .assertWidthIsAtLeast(toggle.widthOf(toggle.timerLabel))
        compose.onNodeWithTag("modeToggleLabel_AMBIENT", useUnmergedTree = true)
            .assertWidthIsAtLeast(toggle.widthOf(toggle.ambientLabel))
    }

    // The narrow case the width floor exists for. Filipino carries the widest
    // mode label of the 47 locales - "Ambient na mode", 115dp at labelLarge
    // against 56dp for English "Ambient" - and 360dp is the common small-phone
    // width, where HomeScreen's 32dp side padding leaves the row 296dp of the
    // 329dp it asks for. The Japanese tests above run at 1280dp, where the row
    // is never squeezed at all, so nothing else here exercises a clamped row.
    @Test
    @Config(sdk = [34], qualifiers = "fil-w360dp-h800dp")
    fun `neither Filipino label is clipped on a 360dp phone`() {
        // AMBIENT selected deliberately: only the selected segment reserves
        // space for the animated checkmark, so this puts the widest label in
        // the segment with the least room left for it.
        val toggle = showHome(mode = AppMode.AMBIENT)

        compose.onNodeWithTag("modeToggleLabel_TIMER", useUnmergedTree = true)
            .assertWidthIsAtLeast(toggle.widthOf(toggle.timerLabel))
        compose.onNodeWithTag("modeToggleLabel_AMBIENT", useUnmergedTree = true)
            .assertWidthIsAtLeast(toggle.widthOf(toggle.ambientLabel))
    }

    // The floor is a minimum request, not a guarantee - the row is still clamped
    // by whatever width it is handed. Below roughly 340dp the widest label stops
    // fitting, and what matters then is that it truncates visibly instead of
    // being cut off mid-glyph. 320dp is the narrowest width the app supports and
    // the first one where Filipino overflows, so it is where that degradation is
    // observable.
    @Test
    @Config(sdk = [34], qualifiers = "fil-w320dp-h800dp")
    fun `the widest label ellipsizes rather than being cut off when it cannot fit`() {
        val toggle = showHome(mode = AppMode.AMBIENT)

        // Establishes that this qualifier really does overflow. Without it the
        // ellipsis assertion below would pass vacuously the moment the label
        // started fitting again.
        assertThat(renderedWidthOf("modeToggleLabel_AMBIENT").value)
            .isLessThan(toggle.widthOf(toggle.ambientLabel).value)

        assertThat(textLayoutOf("modeToggleLabel_AMBIENT").isLineEllipsized(0)).isTrue()
    }

    // If @GraphicsMode(NATIVE) were silently giving the same near-zero CJK
    // metrics LEGACY does, every width above would collapse on both sides of its
    // comparison at once - rendered label and reference alike - and the clipping
    // tests would hold no matter how badly the real labels were clipped. Latin
    // text keeps its metrics under either mode, so requiring the Japanese string
    // to out-measure the English one is a comparison that only survives real
    // shaping. The literals are fixtures for that engine check, not app copy, so
    // they stay pinned here rather than being read from resources.
    @Test
    @Config(sdk = [34], qualifiers = "ja-w1280dp-h800dp")
    fun `Japanese shapes wider than English`() {
        val toggle = showHome()

        assertThat(toggle.widthOf("タイマー").value)
            .isGreaterThan(toggle.widthOf("Timer").value)
        assertThat(toggle.widthOf("アンビエント").value)
            .isGreaterThan(toggle.widthOf("Ambient").value)
    }
}
