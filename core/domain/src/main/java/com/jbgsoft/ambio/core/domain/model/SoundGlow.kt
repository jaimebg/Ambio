package com.jbgsoft.ambio.core.domain.model

import androidx.compose.ui.graphics.Color

/**
 * The colour a single sound contributes to the mix gradient, one per sound —
 * unlike [SoundTheme], where twelve sounds share six palettes.
 *
 * That sharing is deliberate and stays: it keeps the Material palette space
 * small enough for ThemeContrastTest to enumerate. But it cannot drive a
 * gradient. Five groups of sounds hold byte-identical themes, so `ocean +
 * stream` would render a gradient from #0A1929 to #0A1929 — a flat screen,
 * indistinguishable from a single sound. Hence a second, finer colour axis.
 *
 * These are not the theme colours darkened. The six theme backgrounds sit at
 * L* 3.9-16.9 with their closest pairs only 7-11 dE apart, which reads as flat
 * across a whole screen; and the six primaries cluster into two hue families
 * (RAIN/OCEAN/NOISE all blue-violet, FIREPLACE/CAVE both orange). Neither role
 * can carry a gradient, so these values are solved fresh.
 *
 * Solved, literally: hill climbing with restarts, maximising the minimum
 * pairwise CIELAB distance subject to L* in [12, 26], chroma inside sRGB, and a
 * per-sound hue window fixed by meaning (fireplace orange, rain blue-violet).
 * Achieved min dE 17.63 (forest/crickets), median 27.4. Two earlier hand-picked
 * attempts collapsed to dE 3-6 on wind/white_noise, cafe/brown_noise and
 * forest/crickets, because tinting every accent uniformly into black compresses
 * exactly the low-chroma sounds. Separating by lightness *as well as* hue is
 * what fixes those three pairs.
 *
 * FOREST, BIRDS and CRICKETS stay inside the green family on purpose. They are
 * one place at different hours, and a mix of the three should read as one wood
 * with depth rather than three unrelated sounds. They still clear the bound at
 * dE 17.6.
 *
 * Declaration order is catalogue order, matching SoundRepositoryImpl, because
 * [gradientOf] sorts by ordinal to make its output independent of the order the
 * user happened to switch sounds on.
 */
enum class SoundGlow(val color: Color) {
    RAIN(Color(0xFF181D3E)),
    FIREPLACE(Color(0xFF582A1F)),
    CAFE(Color(0xFF3D301A)),
    FOREST(Color(0xFF0D472F)),
    BIRDS(Color(0xFF213007)),
    CRICKETS(Color(0xFF062519)),
    OCEAN(Color(0xFF164062)),
    STREAM(Color(0xFF01454C)),
    CAVE(Color(0xFF50344B)),
    WIND(Color(0xFF07222D)),
    WHITE_NOISE(Color(0xFF3C3E3F)),
    BROWN_NOISE(Color(0xFF2E1A1D))
}
