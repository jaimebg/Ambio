package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color

/** The near-black every mix is painted onto, under the translucent blobs. */
private val GRADIENT_BASE = Color(0xFF0C0E12)

/**
 * Three colours and the ground they sit on. Always three, whatever the mix size
 * — see [gradientOf].
 */
data class MixGradient(
    val base: Color,
    val stops: List<Color>
)

/**
 * One stop per active sound, so a mix of N sounds looks like N sounds instead of
 * the single averaged colour [mixPalettes] produces.
 *
 * The stop count is fixed at three no matter how many sounds are playing, and
 * that is the decision the rest of the design rests on. With a variable count
 * there is nothing to interpolate stop-against-stop, and removing the middle
 * sound of a three-mix would teleport a blob from one anchor to another. With
 * three always, nothing ever moves: adding or removing a sound is a colour
 * crossfade, and each stop animates independently on the 400 ms the theme
 * already uses.
 *
 * Sorted by ordinal first. Unlike [mixPalettes], which is commutative because it
 * averages, `(a, mid, b)` and `(b, mid, a)` are different lists here — without
 * sorting, order independence would simply be false. In practice it is a no-op,
 * since the repository already normalises to catalogue order and the enum is
 * declared in that order, but it makes the property true by construction rather
 * than by the caller's good manners.
 *
 * Volume is not an input. That is what keeps the reachable space finite, so
 * MixGradientTest can enumerate all 298 of them instead of sampling.
 */
fun gradientOf(glows: List<SoundGlow>): MixGradient {
    require(glows.isNotEmpty()) { "The active mix is never empty" }
    require(glows.size <= MixCodec.MAX_ACTIVE_SOUNDS) {
        "The mix never holds more than ${MixCodec.MAX_ACTIVE_SOUNDS} sounds"
    }

    val colors = glows.sortedBy { it.ordinal }.map { it.color }
    val stops = when (colors.size) {
        1 -> List(MixCodec.MAX_ACTIVE_SOUNDS) { colors[0] }
        2 -> listOf(colors[0], averageOf(colors), colors[1])
        else -> colors
    }

    return MixGradient(base = GRADIENT_BASE, stops = stops)
}
