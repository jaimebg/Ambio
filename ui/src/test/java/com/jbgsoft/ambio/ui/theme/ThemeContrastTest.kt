package com.jbgsoft.ambio.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG AA: 3.0 for UI components, 4.5 for normal text.
 * Guards the palette so a future colour tweak cannot silently make the app
 * unreadable — four of five themes failed before Phase 2.
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

    @Test
    fun `primary is legible against background and surface in every theme`() {
        SoundTheme.entries.forEach { theme ->
            assertThat(contrast(theme.primary, theme.background)).isAtLeast(3.0)
            assertThat(contrast(theme.primary, theme.surface)).isAtLeast(3.0)
        }
    }

    @Test
    fun `onPrimary is legible on primary and on secondary in every theme`() {
        SoundTheme.entries.forEach { theme ->
            assertThat(contrast(theme.onPrimary, theme.primary)).isAtLeast(4.5)
            assertThat(contrast(theme.onPrimary, theme.secondary)).isAtLeast(4.5)
        }
    }

    @Test
    fun `white is legible on the container colour used by Theme`() {
        // Theme.kt maps primaryContainer and secondaryContainer to surfaceVariant,
        // and their on- roles to white. This is the pair that guards that mapping.
        SoundTheme.entries.forEach { theme ->
            assertThat(contrast(Color.White, theme.surfaceVariant)).isAtLeast(4.5)
        }
    }
}
