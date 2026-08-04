package com.jbgsoft.ambio.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes
import com.jbgsoft.ambio.core.domain.model.toPalette
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG AA: 3.0 for UI components, 4.5 for normal text.
 *
 * Because the mix ignores per-sound volume, the palette space is finite — the
 * 31 non-empty subsets of five sounds — so this test enumerates all of them
 * instead of sampling. A weighted mix would make the space continuous and only
 * sampling would be possible.
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

    /** All 31 non-empty subsets of the five themes, each with a readable label. */
    private fun allMixes(): List<Pair<String, AmbioPalette>> {
        val themes = SoundTheme.entries
        return (1 until (1 shl themes.size)).map { bits ->
            val subset = themes.filterIndexed { index, _ -> (bits shr index) and 1 == 1 }
            subset.joinToString("+") { it.name } to mixPalettes(subset)
        }
    }

    @Test
    fun `there are exactly 31 mixes`() {
        assertThat(allMixes()).hasSize(31)
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
        // the spec's table is wrong too.
        val rainFire = mixPalettes(listOf(SoundTheme.RAIN, SoundTheme.FIREPLACE))
        assertThat(rainFire.primary).isEqualTo(Color(0xFFA66F78))
        assertThat(rainFire.onPrimary).isEqualTo(Color.Black)
        assertThat(rainFire.background).isEqualTo(Color(0xFF241C26))

        val fireOcean = mixPalettes(listOf(SoundTheme.FIREPLACE, SoundTheme.OCEAN))
        assertThat(fireOcean.primary).isEqualTo(Color(0xFF77756D))

        val everything = mixPalettes(SoundTheme.entries)
        assertThat(everything.primary).isEqualTo(Color(0xFF6D8187))
        assertThat(everything.background).isEqualTo(Color(0xFF1B1E22))
    }
}
