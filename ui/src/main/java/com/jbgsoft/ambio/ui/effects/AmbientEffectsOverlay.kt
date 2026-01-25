package com.jbgsoft.ambio.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.jbgsoft.ambio.core.domain.model.SoundTheme

@Composable
fun AmbientEffectsOverlay(
    isPlaying: Boolean,
    soundTheme: SoundTheme,
    modifier: Modifier = Modifier
) {
    val config = soundTheme.toEffectConfig()
    val particleState = rememberParticleSystemState(config)

    // This drives continuous updates and returns a value that changes every frame
    val animationTick = useParticleAnimation(
        state = particleState,
        isPlaying = isPlaying
    )

    // Read state values - these trigger recomposition when they change
    val intensity = particleState.intensity
    val particles = particleState.particles

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                particleState.canvasSize = size.toSize()
            }
    ) {
        // Reference animationTick to ensure Canvas redraws
        val tick = animationTick

        // Draw all particles as simple circles
        // Multiply alpha by intensity so particles fade smoothly with the overall effect
        for (i in particles.indices) {
            val particle = particles[i]
            val effectiveAlpha = (particle.alpha * intensity.coerceAtLeast(0.1f)).coerceIn(0.02f, 1f)
            drawCircle(
                color = particle.color.copy(alpha = effectiveAlpha),
                radius = particle.size.coerceAtLeast(4f),
                center = particle.position
            )
        }
    }
}
