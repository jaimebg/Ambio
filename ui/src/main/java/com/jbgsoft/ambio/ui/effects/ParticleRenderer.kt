package com.jbgsoft.ambio.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SPRITE_PX = 64
private const val LARGE_SPRITE_PX = 128
private const val MIN_DRAW_ALPHA = 0.02f
private const val EMBER_FLICKER_BASE = 0.72f
private const val EMBER_FLICKER_SWING = 0.28f
private const val EMBER_FLICKER_RATE = 0.011f

/**
 * Colours stay per sound rather than following the averaged palette. A mix of
 * three already tends toward grey-blue by design, and tinting the particles with
 * it would mute them exactly when telling them apart matters most.
 *
 * Four entries per type, matching COLORS_PER_TYPE in ParticleField.
 */
fun colorsFor(type: ParticleType): List<Color> = when (type) {
    ParticleType.DROPLET -> listOf(
        Color(0xFF5C7AEA), Color(0xFF8B9DC3), Color(0xFF7B93D8), Color(0xFF6B85CC)
    )
    ParticleType.EMBER -> listOf(
        Color(0xFFE85D04), Color(0xFFFAA307), Color(0xFFFF8800), Color(0xFFFFAA33)
    )
    ParticleType.LEAF -> listOf(
        Color(0xFF2D6A4F), Color(0xFF52B788), Color(0xFF40916C), Color(0xFF74C69D)
    )
    ParticleType.BUBBLE -> listOf(
        Color(0xFF48CAE4), Color(0xFF90E0EF), Color(0xFF00B4D8), Color(0xFFADE8F4)
    )
    ParticleType.WISP -> listOf(
        Color(0xFF6B5B4F), Color(0xFF9C8A7C), Color(0xFF857567), Color(0xFFB0A090)
    )
}

fun SoundTheme.toParticleType(): ParticleType = when (this) {
    SoundTheme.RAIN -> ParticleType.DROPLET
    SoundTheme.FIREPLACE -> ParticleType.EMBER
    SoundTheme.FOREST -> ParticleType.LEAF
    SoundTheme.OCEAN -> ParticleType.BUBBLE
    SoundTheme.CAVE -> ParticleType.WISP
}

/**
 * One bitmap per (type, colour), baked once. At most three types are active and
 * each has four colours, so this is at most twelve sprites — under 200 KB — and
 * it keeps the draw loop free of per-particle shader allocation.
 */
@Composable
fun rememberParticleSprites(types: Set<ParticleType>): Map<ParticleType, List<ImageBitmap>> =
    remember(types) {
        types.associateWith { type ->
            colorsFor(type).map { color -> bakeSprite(color, specFor(type).coreFraction, type) }
        }
    }

private fun bakeSprite(color: Color, coreFraction: Float, type: ParticleType): ImageBitmap {
    val size = if (type == ParticleType.WISP) LARGE_SPRITE_PX else SPRITE_PX
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val radius = size / 2f

    // A duplicate 0f stop is not a valid gradient, so a coreless profile gets two
    // stops instead of three.
    val colors: List<Color>
    val stops: List<Float>
    if (coreFraction > 0f) {
        colors = listOf(color, color, Color.Transparent)
        stops = listOf(0f, coreFraction, 1f)
    } else {
        colors = listOf(color, Color.Transparent)
        stops = listOf(0f, 1f)
    }

    val paint = Paint().apply {
        shader = RadialGradientShader(
            center = Offset(radius, radius),
            radius = radius,
            colors = colors,
            colorStops = stops
        )
    }
    canvas.drawCircle(Offset(radius, radius), radius, paint)
    return bitmap
}

/**
 * Two passes, so the blend mode is set twice per frame rather than once per
 * particle. Additive types are drawn last so they read as light rather than
 * paint.
 */
fun DrawScope.drawParticles(
    particles: List<Particle>,
    sprites: Map<ParticleType, List<ImageBitmap>>,
    intensity: Float,
    frameTimeMs: Float
) {
    drawPass(particles, sprites, intensity, frameTimeMs, additive = false)
    drawPass(particles, sprites, intensity, frameTimeMs, additive = true)
}

private fun DrawScope.drawPass(
    particles: List<Particle>,
    sprites: Map<ParticleType, List<ImageBitmap>>,
    intensity: Float,
    frameTimeMs: Float,
    additive: Boolean
) {
    val blendMode = if (additive) BlendMode.Plus else BlendMode.SrcOver

    for (index in particles.indices) {
        val particle = particles[index]
        val spec = specFor(particle.type)
        if (spec.additive != additive) continue

        val sprite = sprites[particle.type]?.getOrNull(particle.colorIndex) ?: continue

        var alpha = particle.baseAlpha * particle.lifeAlpha * intensity
        if (particle.type == ParticleType.EMBER) {
            // Flicker runs on frame time rather than particle age, so it is a real
            // use of the per-frame value the overlay reads to invalidate the draw
            // phase, and embers keep flickering at a steady rate regardless of age.
            alpha *= EMBER_FLICKER_BASE +
                EMBER_FLICKER_SWING * sin(frameTimeMs * EMBER_FLICKER_RATE + particle.seed)
        }
        if (alpha < MIN_DRAW_ALPHA) continue

        val diameter = (particle.radiusPx * 2f).roundToInt().coerceAtLeast(1)
        drawImage(
            image = sprite,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sprite.width, sprite.height),
            dstOffset = IntOffset(
                (particle.x - particle.radiusPx).roundToInt(),
                (particle.y - particle.radiusPx).roundToInt()
            ),
            dstSize = IntSize(diameter, diameter),
            alpha = alpha.coerceIn(0f, 1f),
            blendMode = blendMode
        )
    }
}
