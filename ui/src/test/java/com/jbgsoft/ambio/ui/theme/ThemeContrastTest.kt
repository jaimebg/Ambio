package com.jbgsoft.ambio.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.MixCodec
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import com.jbgsoft.ambio.core.domain.model.toPalette
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG AA: 3.0 for UI components, 4.5 for normal text.
 *
 * Because the mix ignores per-sound volume, the palette space is finite, so this
 * test enumerates it instead of sampling. With the ceiling at three sounds the
 * reachable space is the 25 non-empty subsets of size <= 3 — a strict subset of
 * the 31 this test covered before, so narrowing it cannot hide a regression.
 */
class ThemeContrastTest {

    private fun Color.relativeLuminance(): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Every reachable mix: the non-empty subsets of at most MAX_ACTIVE_SOUNDS themes. */
    private fun allMixes(): List<Pair<String, AmbioPalette>> {
        val themes = SoundTheme.entries
        return (1 until (1 shl themes.size))
            .map { bits -> themes.filterIndexed { index, _ -> (bits shr index) and 1 == 1 } }
            .filter { it.size <= MixCodec.MAX_ACTIVE_SOUNDS }
            .map { subset -> subset.joinToString("+") { it.name } to mixPalettes(subset) }
    }

    @Test
    fun `there are exactly 25 reachable mixes`() {
        assertThat(allMixes()).hasSize(25)
    }

    @Test
    fun `no reachable mix exceeds the ceiling`() {
        val themes = SoundTheme.entries
        val subsets = (1 until (1 shl themes.size))
            .map { bits -> themes.filterIndexed { index, _ -> (bits shr index) and 1 == 1 } }
            .filter { it.size <= MixCodec.MAX_ACTIVE_SOUNDS }

        assertThat(subsets).isNotEmpty()
        subsets.forEach { subset ->
            assertWithMessage("%s", subset.joinToString("+") { it.name })
                .that(subset.size).isAtMost(MixCodec.MAX_ACTIVE_SOUNDS)
        }
    }

    @Test
    fun `primary is legible against background and surface in every mix`() {
        allMixes().forEach { (label, palette) ->
            assertWithMessage("%s: primary on background", label)
                .that(contrast(palette.primary, palette.background)).isAtLeast(3.0)
            assertWithMessage("%s: primary on surface", label)
                .that(contrast(palette.primary, palette.surface)).isAtLeast(3.0)
        }
    }

    @Test
    fun `onPrimary is legible on primary and on secondary in every mix`() {
        allMixes().forEach { (label, palette) ->
            assertWithMessage("%s: onPrimary on primary", label)
                .that(contrast(palette.onPrimary, palette.primary)).isAtLeast(4.5)
            assertWithMessage("%s: onPrimary on secondary", label)
                .that(contrast(palette.onPrimary, palette.secondary)).isAtLeast(4.5)
        }
    }

    @Test
    fun `white is legible on the container colour used by Theme in every mix`() {
        // Theme.kt maps primaryContainer and secondaryContainer to surfaceVariant,
        // and their on- roles to white. This is the pair that guards that mapping.
        allMixes().forEach { (label, palette) ->
            assertWithMessage("%s: white on surfaceVariant", label)
                .that(contrast(Color.White, palette.surfaceVariant)).isAtLeast(4.5)
        }
    }

    @Test
    fun `a single sound keeps its hand-tuned palette untouched`() {
        SoundTheme.entries.forEach { theme ->
            assertWithMessage("%s must not be altered by the mixing rules", theme.name)
                .that(mixPalettes(listOf(theme))).isEqualTo(theme.toPalette())
        }
    }

    @Test
    fun `mixing is independent of the order the sounds were activated`() {
        val forwards = mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE))
        val backwards = mixPalettes(listOf(SoundTheme.FIREPLACE, SoundTheme.RAIN))

        assertThat(forwards).isEqualTo(backwards)
    }

    @Test
    fun `known mixes produce the tabulated colours`() {
        // Half-up rounding. These exact values are in the spec; if they change,
        // the spec's table is wrong too. Only pairs are tabulated here — the
        // five-sound mix these once checked is no longer reachable.
        val rainFire = mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE))
        assertThat(rainFire.primary).isEqualTo(Color(0xFFA66F78))
        assertThat(rainFire.onPrimary).isEqualTo(Color.Black)
        assertThat(rainFire.background).isEqualTo(Color(0xFF241C26))

        val fireOcean = mixPalettes(listOf(SoundTheme.FIREPLACE, SoundTheme.OCEAN))
        assertThat(fireOcean.primary).isEqualTo(Color(0xFF77756D))
    }
}
