package com.jbgsoft.ambio.media

import androidx.annotation.ColorInt

/**
 * The mix gradient as plain ARGB: the near-black ground and the three stops laid on it.
 * It is all media needs to paint the session artwork the system media card takes its
 * colours from (#23).
 *
 * It mirrors core:domain's MixGradient rather than reusing it, because media does not
 * depend on core:domain — the same reason [MixEntry] is not a Sound. Whoever can see
 * both converts; today that is HomeViewModel and core:di.
 */
data class MixColors(
    @param:ColorInt val base: Int,
    val stops: List<Int>
) {
    init {
        require(stops.size == STOP_COUNT) { "A mix gradient always has $STOP_COUNT stops" }
    }

    internal companion object {
        /** MixGradient's own count; [MixArtwork.ANCHORS] places one blob per stop. */
        const val STOP_COUNT = 3
    }
}
