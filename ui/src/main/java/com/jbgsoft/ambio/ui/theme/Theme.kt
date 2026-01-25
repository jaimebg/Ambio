package com.jbgsoft.ambio.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jbgsoft.ambio.core.domain.model.SoundTheme

private const val THEME_ANIMATION_DURATION = 400

@Composable
fun AmbioTheme(
    soundTheme: SoundTheme = SoundTheme.RAIN,
    content: @Composable () -> Unit
) {
    val animatedPrimary by animateColorAsState(
        targetValue = soundTheme.primary,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "primary"
    )
    val animatedOnPrimary by animateColorAsState(
        targetValue = soundTheme.onPrimary,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "onPrimary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = soundTheme.secondary,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "secondary"
    )
    val animatedBackground by animateColorAsState(
        targetValue = soundTheme.background,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "background"
    )
    val animatedSurface by animateColorAsState(
        targetValue = soundTheme.surface,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "surface"
    )
    val animatedSurfaceVariant by animateColorAsState(
        targetValue = soundTheme.surfaceVariant,
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "surfaceVariant"
    )

    val colorScheme = darkColorScheme(
        primary = animatedPrimary,
        onPrimary = animatedOnPrimary,
        primaryContainer = animatedSurfaceVariant,
        onPrimaryContainer = animatedOnPrimary,
        secondary = animatedSecondary,
        onSecondary = animatedOnPrimary,
        secondaryContainer = animatedSurfaceVariant,
        onSecondaryContainer = animatedOnPrimary,
        background = animatedBackground,
        onBackground = Color.White,
        surface = animatedSurface,
        onSurface = Color.White,
        surfaceVariant = animatedSurfaceVariant,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        outline = animatedSecondary.copy(alpha = 0.5f),
        outlineVariant = animatedSecondary.copy(alpha = 0.3f)
    )

    // Update system bar colors to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = animatedBackground.toArgb()
            window.navigationBarColor = animatedBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AmbioTypography,
        shapes = AmbioShapes,
        content = content
    )
}
