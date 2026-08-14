package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/** The six colour roles the app themes, independent of where they came from. */
data class AmbioPalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color
)

fun SoundTheme.toPalette(): AmbioPalette = AmbioPalette(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    background = background,
    surface = surface,
    surfaceVariant = surfaceVariant
)

private const val TEXT_CONTRAST = 4.5
private const val ADJUST_STEP = 0.01f
private const val MAX_ADJUST_STEPS = 100

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
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Per-channel arithmetic mean, half-up, in 8-bit space. Shared with [gradientOf]. */
internal fun averageOf(colors: List<Color>): Color {
    fun mean(channel: (Color) -> Float): Int =
        (colors.sumOf { (channel(it) * 255f).roundToInt() }.toFloat() / colors.size).roundToInt()
    return Color(mean { it.red }, mean { it.green }, mean { it.blue })
}

private fun Color.lightenOneStep(): Color = Color(
    (red * 255f + (255f - red * 255f) * ADJUST_STEP).roundToInt(),
    (green * 255f + (255f - green * 255f) * ADJUST_STEP).roundToInt(),
    (blue * 255f + (255f - blue * 255f) * ADJUST_STEP).roundToInt()
)

private fun Color.darkenOneStep(): Color = Color(
    (red * 255f * (1f - ADJUST_STEP)).roundToInt(),
    (green * 255f * (1f - ADJUST_STEP)).roundToInt(),
    (blue * 255f * (1f - ADJUST_STEP)).roundToInt()
)

private fun Color.adjustedUntilLegible(against: Color): Color {
    var result = this
    var steps = 0
    while (contrast(against, result) < TEXT_CONTRAST && steps < MAX_ADJUST_STEPS) {
        result = if (against == Color.Black) result.lightenOneStep() else result.darkenOneStep()
        steps++
    }
    return result
}

/**
 * A single sound keeps its hand-tuned palette untouched. Two or more average all
 * six roles per channel, ignoring volume — so the colour changes only when a sound
 * is added or removed, never when a slider moves, which is what keeps the palette
 * space finite and exhaustively testable.
 *
 * The on- colours are the exception: averaging them is what made 26 of the 31
 * mixes fail WCAG AA. Each theme's onPrimary is a tinted background, and their
 * mean lands too light against a primary that has drifted to mid-tone. They are
 * derived instead, and the primary and secondary are nudged if that is still short.
 */
fun mixPalettes(themes: List<SoundTheme>): AmbioPalette {
    require(themes.isNotEmpty()) { "The active mix is never empty" }
    if (themes.size == 1) return themes.single().toPalette()

    var primary = averageOf(themes.map { it.primary })
    var secondary = averageOf(themes.map { it.secondary })

    val on = listOf(Color.White, Color.Black).maxByOrNull { candidate ->
        minOf(contrast(candidate, primary), contrast(candidate, secondary))
    } ?: Color.Black

    primary = primary.adjustedUntilLegible(against = on)
    secondary = secondary.adjustedUntilLegible(against = on)

    return AmbioPalette(
        primary = primary,
        onPrimary = on,
        secondary = secondary,
        background = averageOf(themes.map { it.background }),
        surface = averageOf(themes.map { it.surface }),
        surfaceVariant = averageOf(themes.map { it.surfaceVariant })
    )
}
