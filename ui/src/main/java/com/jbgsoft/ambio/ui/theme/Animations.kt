package com.jbgsoft.ambio.ui.theme

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

object AmbioAnimations {
    const val THEME_TRANSITION_DURATION = 400
    const val GLOW_ANIMATION_DURATION = 1500
    const val PLAY_PAUSE_MORPH_DURATION = 200
}

@Composable
fun rememberGlowAlpha(isAnimating: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AmbioAnimations.GLOW_ANIMATION_DURATION,
                easing = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    return if (isAnimating) glowAlpha else 0.3f
}
