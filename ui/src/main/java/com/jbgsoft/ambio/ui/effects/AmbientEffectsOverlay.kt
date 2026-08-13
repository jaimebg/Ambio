package com.jbgsoft.ambio.ui.effects

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jbgsoft.ambio.core.domain.model.SoundTheme

/**
 * Placeholder. The simulation, the renderer and the real overlay land in the
 * tasks that follow; this keeps the signature HomeScreen already calls so the
 * module compiles — and so the unit tests for the new pure types can run at
 * all, since testDebugUnitTest depends on compileDebugKotlin.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun AmbientEffectsOverlay(
    isPlaying: Boolean,
    soundTheme: SoundTheme,
    modifier: Modifier = Modifier
) {
    // Intentionally empty until Task 9.
}
