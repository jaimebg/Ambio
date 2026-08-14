package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow

/**
 * Because the gradient ignores per-sound volume, the reachable space is finite,
 * so this enumerates it rather than sampling — the same bargain ThemeContrastTest
 * makes. Twelve glows with the ceiling at three sounds gives the 298 non-empty
 * subsets of size <= 3 (12 + 66 + 220), against that test's 41.
 */
class MixGradientTest {

    private fun Color.relativeLuminance(): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrastWithWhite(color: Color): Double =
        1.05 / (color.relativeLuminance() + 0.05)

    /** Per-channel mean, matching how two stops are blended into a midpoint. */
    private fun blend(a: Color, b: Color): Color = Color(
        ((a.red + b.red) / 2f), ((a.green + b.green) / 2f), ((a.blue + b.blue) / 2f)
    )

    private fun allMixes(): List<Pair<String, MixGradient>> {
        val glows = SoundGlow.entries
        return (1 until (1 shl glows.size))
            .map { bits -> glows.filterIndexed { index, _ -> (bits shr index) and 1 == 1 } }
            .filter { it.size <= MixCodec.MAX_ACTIVE_SOUNDS }
            .map { subset -> subset.joinToString("+") { it.name } to gradientOf(subset) }
    }

    @Test
    fun `there are exactly 298 reachable gradients`() {
        assertThat(allMixes()).hasSize(298)
    }

    @Test
    fun `every gradient has exactly three stops`() {
        allMixes().forEach { (label, gradient) ->
            assertWithMessage(label).that(gradient.stops).hasSize(3)
        }
    }

    @Test
    fun `one sound repeats its own colour three times`() {
        SoundGlow.entries.forEach { glow ->
            assertWithMessage(glow.name)
                .that(gradientOf(listOf(glow)).stops)
                .containsExactly(glow.color, glow.color, glow.color)
        }
    }

    @Test
    fun `two sounds put their blend between them`() {
        val gradient = gradientOf(listOf(SoundGlow.RAIN, SoundGlow.FIREPLACE))
        assertThat(gradient.stops).containsExactly(
            Color(0xFF181D3E), Color(0xFF38242F), Color(0xFF582A1F)
        ).inOrder()
    }

    @Test
    fun `removing the middle sound leaves the outer stops untouched`() {
        val three = gradientOf(listOf(SoundGlow.RAIN, SoundGlow.FOREST, SoundGlow.OCEAN))
        val two = gradientOf(listOf(SoundGlow.RAIN, SoundGlow.OCEAN))

        assertThat(two.stops[0]).isEqualTo(three.stops[0])
        assertThat(two.stops[2]).isEqualTo(three.stops[2])
    }

    @Test
    fun `the gradient is independent of the order sounds were activated`() {
        assertThat(gradientOf(listOf(SoundGlow.FIREPLACE, SoundGlow.RAIN)))
            .isEqualTo(gradientOf(listOf(SoundGlow.RAIN, SoundGlow.FIREPLACE)))
    }

    @Test
    fun `the mixes that used to render flat now separate`() {
        // These two pairs share a SoundTheme, so under mixPalettes they produce
        // a single colour and would have rendered a gradient to itself.
        assertThat(gradientOf(listOf(SoundGlow.OCEAN, SoundGlow.STREAM)).stops)
            .containsExactly(Color(0xFF164062), Color(0xFF0C4357), Color(0xFF01454C))
            .inOrder()
        assertThat(gradientOf(listOf(SoundGlow.WHITE_NOISE, SoundGlow.BROWN_NOISE)).stops)
            .containsExactly(Color(0xFF3C3E3F), Color(0xFF352C2E), Color(0xFF2E1A1D))
            .inOrder()
    }

    @Test
    fun `white is legible on every stop of every mix`() {
        allMixes().forEach { (label, gradient) ->
            gradient.stops.forEachIndexed { index, stop ->
                assertWithMessage("%s: white on stop %s", label, index)
                    .that(contrastWithWhite(stop)).isAtLeast(4.5)
            }
        }
    }

    @Test
    fun `white is legible where two blobs overlap in every mix`() {
        // Blobs are drawn translucent and do overlap, so the composite is what
        // text actually sits on. Compositing dark over dark cannot escape the
        // hull of the stops, so a 50/50 blend is the representative worst case.
        allMixes().forEach { (label, gradient) ->
            gradient.stops.forEachIndexed { i, a ->
                gradient.stops.drop(i + 1).forEach { b ->
                    assertWithMessage("%s: white on overlap", label)
                        .that(contrastWithWhite(blend(a, b))).isAtLeast(4.5)
                }
            }
        }
    }

    @Test
    fun `white is legible on the base`() {
        assertThat(contrastWithWhite(gradientOf(listOf(SoundGlow.RAIN)).base)).isAtLeast(4.5)
    }

    @Test
    fun `a mix above the ceiling is rejected`() {
        val tooMany = SoundGlow.entries.take(MixCodec.MAX_ACTIVE_SOUNDS + 1)
        try {
            gradientOf(tooMany)
            throw AssertionError("expected gradientOf to reject ${tooMany.size} sounds")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("never holds more than")
        }
    }

    @Test
    fun `an empty mix is rejected`() {
        try {
            gradientOf(emptyList())
            throw AssertionError("expected gradientOf to reject an empty mix")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("never empty")
        }
    }
}
