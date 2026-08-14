package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color

/** The near-black every mix is painted onto, under the translucent blobs. */
private val GRADIENT_BASE = Color(0xFF0C0E12)

/**
 * How many colours a gradient carries. Must stay equal to `ANCHORS.size` in
 * MixGradientBackground.kt (`:ui`) — one anchor paints one stop, and a stop with
 * no anchor is simply never drawn.
 *
 * Deliberately its own constant and not [MixCodec.MAX_ACTIVE_SOUNDS], even
 * though both are 3 today. They are independent questions: how many sounds may
 * play at once, versus how many colours the background is made of. Re-unifying
 * them would mean raising the mix ceiling to four silently gave a one-sound mix
 * four stops while a two-sound mix still got three, and `ANCHORS` would drop the
 * extra without a word.
 */
private const val STOP_COUNT = 3

/**
 * Three colours and the ground they sit on. Always three, whatever the mix size
 * — see [gradientOf].
 */
data class MixGradient(
    val base: Color,
    val stops: List<Color>
) {
    // `:ui` builds these too (the cross-fade rebuilds one per frame). A
    // wrong-sized list would otherwise surface as an IndexOutOfBoundsException
    // in the draw phase, with a stack trace nowhere near whoever built it.
    init {
        require(stops.size == STOP_COUNT) { "A mix gradient always has $STOP_COUNT stops" }
    }
}

/**
 * One stop per active sound, so a mix of N sounds looks like N sounds instead of
 * the single averaged colour [mixPalettes] produces.
 *
 * The stop count is fixed at [STOP_COUNT] no matter how many sounds are
 * playing, and that is the decision the rest of the design rests on. With a
 * variable count there is nothing to interpolate stop-against-stop, and
 * removing the middle sound of a three-mix would teleport a blob from one
 * anchor to another. With three always, nothing ever moves: adding or removing
 * a sound is a colour crossfade, and each stop animates independently on the
 * 400 ms the theme already uses.
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
        // STOP_COUNT, not the mix ceiling above: this is how many stops a
        // gradient has, which is a different quantity that happens to share the
        // same value. See STOP_COUNT.
        1 -> List(STOP_COUNT) { colors[0] }
        2 -> listOf(colors[0], averageOf(colors), colors[1])
        else -> colors
    }

    return MixGradient(base = GRADIENT_BASE, stops = stops)
}
