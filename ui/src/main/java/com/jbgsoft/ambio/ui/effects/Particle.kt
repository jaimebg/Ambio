package com.jbgsoft.ambio.ui.effects

enum class ParticleType {
    DROPLET,
    EMBER,
    LEAF,
    BUBBLE,
    WISP
}

private const val FADE_IN_DURATION = 0.15f  // First 15% of life: fade in
private const val FADE_OUT_START = 0.7f     // Last 30% of life: fade out

/**
 * Deliberately free of Compose types. Position is in px because the simulation
 * runs in px; the colour is an index into the renderer's per-type palette, so a
 * particle whose sound has already left the mix still paints correctly while it
 * dies. Mutable fields are plain vars: nothing here is snapshot state, and the
 * overlay invalidates the draw phase itself.
 */
class Particle(
    var x: Float,
    var y: Float,
    /** Position before the sway offset, so sway never accumulates into the path. */
    var baseX: Float,
    var vx: Float,
    var vy: Float,
    val radiusPx: Float,
    val baseAlpha: Float,
    val lifetimeMs: Long,
    var ageMs: Long,
    val seed: Float,
    val colorIndex: Int,
    val type: ParticleType
) {
    val isAlive: Boolean
        get() = ageMs < lifetimeMs

    val lifeAlpha: Float
        get() {
            val lifeProgress = ageMs.toFloat() / lifetimeMs.toFloat()
            return when {
                lifeProgress < FADE_IN_DURATION -> lifeProgress / FADE_IN_DURATION
                lifeProgress > FADE_OUT_START ->
                    1f - (lifeProgress - FADE_OUT_START) / (1f - FADE_OUT_START)
                else -> 1f
            }.coerceIn(0f, 1f)
        }
}
