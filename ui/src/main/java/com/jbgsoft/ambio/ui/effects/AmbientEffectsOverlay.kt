package com.jbgsoft.ambio.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jbgsoft.ambio.core.domain.model.SoundTheme

/** One active sound as the overlay needs it: a palette identity and a level. */
data class ParticleSource(
    val theme: SoundTheme,
    val weight: Float
)

@Composable
fun AmbientEffectsOverlay(
    isPlaying: Boolean,
    mix: List<ParticleSource>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val field = remember { ParticleField() }

    val sources = remember(mix) {
        mix.map { FieldSource(it.theme.toParticleType(), it.weight) }
    }

    // Every type, not just the active ones. A sound removed from the mix leaves live
    // particles behind to fade out — ParticleField deliberately lets them die rather
    // than culling them — and the renderer skips any particle whose type has no sprite.
    // Keying the cache on the active types would therefore evict a draining sound's
    // sprites and make its particles vanish on the frame it was switched off, which is
    // the exact discontinuity this design exists to avoid. Baking all five is stable, so
    // this never rebuilds: 4 types x 4 colours at 64px (16 KB each) plus wisp's 4 at
    // 128px (64 KB each) is about 512 KB, once.
    val sprites = rememberParticleSprites(remember { ParticleType.entries.toSet() })

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeCount by remember { mutableIntStateOf(0) }

    // Written once per frame and read in the draw lambda. It is what invalidates
    // the draw phase — the particle list is a plain ArrayList, so mutating it
    // signals nothing — and the renderer genuinely consumes it for the ember
    // flicker, so it is not a dummy read.
    var frameTimeMs by remember { mutableFloatStateOf(0f) }

    // Only the ramp is worth saving: walking to Settings and back used to restart
    // the eight-second build-up from zero. The particles themselves refill in
    // under a second.
    var savedIntensity by rememberSaveable { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        field.restoreIntensity(savedIntensity)
        onDispose { savedIntensity = field.intensity }
    }

    // When nothing is playing and the last particle has died there is nothing to
    // simulate, so the loop ends rather than spinning at 60 Hz forever.
    val running by remember { derivedStateOf { isPlaying || activeCount > 0 } }

    // The frame loop is keyed on the field and the lifecycle owner, so it must not
    // capture isPlaying or sources directly: a toggle would not restart it and the
    // loop would keep simulating the state the user just left.
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentSources by rememberUpdatedState(sources)

    val lifecycleOwner = LocalLifecycleOwner.current
    if (running) {
        LaunchedEffect(field, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Reset per resume, so returning from the background does not feed
                // in one enormous delta.
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        val deltaMs = if (last == 0L) 0f else (now - last) / 1_000_000f
                        last = now
                        field.update(
                            deltaMs = deltaMs,
                            isPlaying = currentIsPlaying,
                            sources = currentSources,
                            widthPx = canvasSize.width.toFloat(),
                            heightPx = canvasSize.height.toFloat(),
                            density = density
                        )
                        activeCount = field.particles.size
                        frameTimeMs = now / 1_000_000f
                    }
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        // frameTimeMs is read here, inside the draw lambda, and never in the
        // composable body. That defers the read to the draw phase, so each frame
        // repaints without recomposing — the old overlay recomposed sixty times a
        // second and rebuilt its config every time.
        drawParticles(field.particles, sprites, field.intensity, frameTimeMs)
    }
}
