package com.jbgsoft.ambio.ui.effects

import androidx.compose.ui.graphics.Color
import com.jbgsoft.ambio.core.domain.model.SoundTheme

data class EffectConfig(
    val type: ParticleType,
    val colors: List<Color>,
    val spawnRateRange: ClosedFloatingPointRange<Float>,
    val sizeRange: ClosedFloatingPointRange<Float>,
    val velocityXRange: ClosedFloatingPointRange<Float>,
    val velocityYRange: ClosedFloatingPointRange<Float>,
    val alphaRange: ClosedFloatingPointRange<Float>,
    val lifetimeRange: LongRange,
    val rotationSpeedRange: ClosedFloatingPointRange<Float>,
    val maxParticles: Int
)

fun SoundTheme.toEffectConfig(): EffectConfig = when (this) {
    SoundTheme.RAIN -> EffectConfig(
        type = ParticleType.DROPLET,
        colors = listOf(
            Color(0xFF5C7AEA),
            Color(0xFF8B9DC3),
            Color(0xFF7B93D8),
            Color(0xFF6B85CC)
        ),
        spawnRateRange = 8f..15f,
        sizeRange = 3f..8f,
        velocityXRange = -20f..20f,
        velocityYRange = 400f..600f,
        alphaRange = 0.3f..0.7f,
        lifetimeRange = 2000L..4000L,
        rotationSpeedRange = 0f..0f,
        maxParticles = 60
    )

    SoundTheme.FIREPLACE -> EffectConfig(
        type = ParticleType.EMBER,
        colors = listOf(
            Color(0xFFE85D04),
            Color(0xFFFAA307),
            Color(0xFFFF8800),
            Color(0xFFFFAA33)
        ),
        spawnRateRange = 6f..12f,
        sizeRange = 4f..10f,
        velocityXRange = -30f..30f,
        velocityYRange = -150f..-80f,
        alphaRange = 0.5f..0.9f,
        lifetimeRange = 3000L..5000L,
        rotationSpeedRange = 0f..0f,
        maxParticles = 50
    )

    SoundTheme.FOREST -> EffectConfig(
        type = ParticleType.LEAF,
        colors = listOf(
            Color(0xFF2D6A4F),
            Color(0xFF52B788),
            Color(0xFF40916C),
            Color(0xFF74C69D)
        ),
        spawnRateRange = 3f..6f,
        sizeRange = 8f..16f,
        velocityXRange = 20f..60f,
        velocityYRange = 60f..120f,
        alphaRange = 0.5f..0.8f,
        lifetimeRange = 5000L..8000L,
        rotationSpeedRange = 0.5f..2f,
        maxParticles = 40
    )

    SoundTheme.OCEAN -> EffectConfig(
        type = ParticleType.BUBBLE,
        colors = listOf(
            Color(0xFF48CAE4),
            Color(0xFF90E0EF),
            Color(0xFF00B4D8),
            Color(0xFFADE8F4)
        ),
        spawnRateRange = 5f..10f,
        sizeRange = 6f..14f,
        velocityXRange = -15f..15f,
        velocityYRange = -100f..-50f,
        alphaRange = 0.4f..0.7f,
        lifetimeRange = 4000L..7000L,
        rotationSpeedRange = 0f..0f,
        maxParticles = 50
    )

    SoundTheme.CAVE -> EffectConfig(
        type = ParticleType.WISP,
        colors = listOf(
            Color(0xFF6B5B4F),
            Color(0xFF9C8A7C),
            Color(0xFF857567),
            Color(0xFFB0A090)
        ),
        spawnRateRange = 2f..5f,
        sizeRange = 30f..60f,
        velocityXRange = -30f..30f,
        velocityYRange = -40f..40f,
        alphaRange = 0.1f..0.25f,
        lifetimeRange = 4000L..7000L,
        rotationSpeedRange = 0f..0f,
        maxParticles = 20
    )
}
