package com.jbgsoft.ambio.ui.effects

/**
 * Per-type physics, in dp and dp/s. Converted to px once per frame by the
 * overlay; storing px here is what made particles physically smaller and rain
 * proportionally slower on denser screens.
 *
 * Keyed by [ParticleType] rather than by SoundTheme on purpose: the theme enum
 * carries Color, and this file must not import Compose.
 *
 * [additive] and [coreFraction] are consumed by the renderer but live here so
 * one table describes a type completely. Both are primitives, so they cost the
 * file nothing.
 */
data class ParticleSpec(
    val type: ParticleType,
    /** Radius. */
    val sizeDpRange: ClosedFloatingPointRange<Float>,
    val velocityXDpRange: ClosedFloatingPointRange<Float>,
    val velocityYDpRange: ClosedFloatingPointRange<Float>,
    val alphaRange: ClosedFloatingPointRange<Float>,
    val lifetimeMsRange: LongRange,
    /** 0 means no sway. Applied as a positional offset, never added to velocity. */
    val swayAmplitudeDp: Float,
    /** Per-type population cap on top of the shared budget. Protects fill rate. */
    val ceiling: Int,
    val additive: Boolean,
    /** Fraction of the radius held at full colour before the falloff starts. */
    val coreFraction: Float
)

fun specFor(type: ParticleType): ParticleSpec = when (type) {
    ParticleType.DROPLET -> ParticleSpec(
        type = ParticleType.DROPLET,
        sizeDpRange = 2.0f..4.5f,
        velocityXDpRange = -8f..8f,
        velocityYDpRange = 430f..660f,
        alphaRange = 0.35f..0.75f,
        lifetimeMsRange = 1400L..2200L,
        swayAmplitudeDp = 0f,
        ceiling = 40,
        additive = false,
        coreFraction = 0.45f
    )

    ParticleType.EMBER -> ParticleSpec(
        type = ParticleType.EMBER,
        sizeDpRange = 2.0f..4.0f,
        velocityXDpRange = -12f..12f,
        velocityYDpRange = -60f..-32f,
        alphaRange = 0.55f..0.90f,
        lifetimeMsRange = 3000L..5000L,
        swayAmplitudeDp = 0f,
        ceiling = 32,
        additive = true,
        coreFraction = 0.22f
    )

    ParticleType.LEAF -> ParticleSpec(
        type = ParticleType.LEAF,
        sizeDpRange = 3.5f..6.5f,
        velocityXDpRange = 8f..24f,
        velocityYDpRange = 24f..46f,
        alphaRange = 0.50f..0.80f,
        lifetimeMsRange = 5000L..8000L,
        swayAmplitudeDp = 16f,
        ceiling = 28,
        additive = false,
        coreFraction = 0f
    )

    ParticleType.BUBBLE -> ParticleSpec(
        type = ParticleType.BUBBLE,
        sizeDpRange = 3.0f..6.0f,
        velocityXDpRange = -6f..6f,
        velocityYDpRange = -40f..-20f,
        alphaRange = 0.40f..0.70f,
        lifetimeMsRange = 4000L..7000L,
        swayAmplitudeDp = 5f,
        ceiling = 32,
        additive = false,
        coreFraction = 0.15f
    )

    ParticleType.WISP -> ParticleSpec(
        type = ParticleType.WISP,
        sizeDpRange = 14f..26f,
        velocityXDpRange = -12f..12f,
        velocityYDpRange = -16f..16f,
        alphaRange = 0.08f..0.18f,
        lifetimeMsRange = 4000L..7000L,
        swayAmplitudeDp = 0f,
        ceiling = 16,
        additive = true,
        coreFraction = 0f
    )
}
