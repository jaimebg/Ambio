package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The gradient exists to make a mix of N sounds look like N sounds. That only
 * works if no two sounds are the same colour, which is exactly what this test
 * pins down.
 *
 * The threshold is deliberately looser than the shipped values: the twelve
 * colours were produced by a solver maximising the minimum pairwise distance
 * (achieved 17.63), and the bound here is 12. That gap is headroom for hand
 * retuning a colour to taste without having to re-run the solver, while still
 * failing loudly if a future sound is dropped in next to an existing one.
 */
class SoundGlowTest {

    private fun Color.toLab(): Triple<Double, Double, Double> {
        fun lin(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(red)
        val g = lin(green)
        val b = lin(blue)
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) =
            if (t > 216.0 / 24389.0) t.pow(1.0 / 3.0) else (841.0 / 108.0) * t + 4.0 / 29.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun deltaE(a: Color, b: Color): Double {
        val (l1, a1, b1) = a.toLab()
        val (l2, a2, b2) = b.toLab()
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun Color.relativeLuminance(): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrastWithWhite(color: Color): Double =
        1.05 / (color.relativeLuminance() + 0.05)

    @Test
    fun `there is exactly one glow per sound in the catalogue`() {
        assertThat(SoundGlow.entries).hasSize(12)
    }

    @Test
    fun `no two glows are within 12 dE of each other`() {
        SoundGlow.entries.forEachIndexed { i, a ->
            SoundGlow.entries.drop(i + 1).forEach { b ->
                assertWithMessage("%s vs %s", a.name, b.name)
                    .that(deltaE(a.color, b.color)).isAtLeast(12.0)
            }
        }
    }

    @Test
    fun `every glow stays dark enough to be a background`() {
        SoundGlow.entries.forEach { glow ->
            assertWithMessage("%s is too light to sit behind content", glow.name)
                .that(glow.color.toLab().first).isAtMost(30.0)
        }
    }

    @Test
    fun `white text is legible on every glow`() {
        SoundGlow.entries.forEach { glow ->
            assertWithMessage("white on %s", glow.name)
                .that(contrastWithWhite(glow.color)).isAtLeast(4.5)
        }
    }
}
