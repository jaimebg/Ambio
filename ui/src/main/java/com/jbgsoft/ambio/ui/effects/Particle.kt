package com.jbgsoft.ambio.ui.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class ParticleType {
    DROPLET,
    EMBER,
    LEAF,
    BUBBLE,
    WISP
}

private const val FADE_IN_DURATION = 0.15f  // First 15% of life: fade in
private const val FADE_OUT_START = 0.7f     // Last 30% of life: fade out

data class Particle(
    val id: Long,
    var position: Offset,
    var velocity: Offset,
    var size: Float,
    val baseAlpha: Float,
    var rotation: Float,
    var lifetime: Long,
    var age: Long,
    val color: Color,
    val type: ParticleType
) {
    val isAlive: Boolean
        get() = age < lifetime

    val alpha: Float
        get() {
            val lifeProgress = age.toFloat() / lifetime.toFloat()
            return when {
                // Fade in during first 15% of life
                lifeProgress < FADE_IN_DURATION -> {
                    baseAlpha * (lifeProgress / FADE_IN_DURATION)
                }
                // Fade out during last 30% of life
                lifeProgress > FADE_OUT_START -> {
                    val fadeProgress = (lifeProgress - FADE_OUT_START) / (1f - FADE_OUT_START)
                    baseAlpha * (1f - fadeProgress)
                }
                // Full alpha in the middle
                else -> baseAlpha
            }
        }
}
