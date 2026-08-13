package com.jbgsoft.ambio.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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

    // Only "is there anything to draw", not a count: writing a fresh Int every frame
    // just to answer that would force an apply notification (and a derived-state
    // re-evaluation, if one still read it) once per frame. A Boolean elides the write
    // when it does not change, which is most frames — it only actually flips at the
    // rare moments the last particle dies or the first one spawns.
    var hasParticles by remember { mutableStateOf(false) }

    // Kept as nanos relative to a per-resume origin, not the absolute Choreographer
    // value, and not a Float: converting an absolute Long around 1e14 ns to Float
    // loses precision fast enough that consecutive frames collapse onto the same
    // value at real uptimes (a 120 Hz panel is down to ~16 distinct values a second
    // by one week of uptime, per the review that caught this) — and since this is
    // the draw phase's only invalidation signal, the field would silently stop
    // repainting. Kept as a Long here; only converted to Float at the point of use,
    // where the magnitude is always small.
    var frameTimeNanos by remember { mutableLongStateOf(0L) }

    // Only the ramp is worth saving: walking to Settings and back used to restart
    // the eight-second build-up from zero. The particles themselves refill in
    // under a second. This survives navigation (leaving and re-entering composition
    // without the activity being destroyed) but not activity recreation: the saved
    // state registry parcels whatever savedIntensity already holds during
    // onSaveInstanceState, which runs before onDispose gets a chance to write the
    // field's live intensity into it. That is accepted, not a bug — a rotation
    // simply rebuilds the field and ramps again, same as any other fresh start.
    var savedIntensity by rememberSaveable { mutableFloatStateOf(0f) }
    DisposableEffect(field) {
        field.restoreIntensity(savedIntensity)
        onDispose { savedIntensity = field.intensity }
    }

    // The frame loop is keyed on the field and the lifecycle owner, so it must not
    // capture isPlaying, sources or density directly: none of those changing would
    // restart it, and the loop would keep simulating the state — or the screen
    // density — the user just left.
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentSources by rememberUpdatedState(sources)
    val currentDensity by rememberUpdatedState(density)

    // A plain val, recomputed on every recomposition — there is no `remember` here
    // to freeze isPlaying at some earlier value, so reading the raw parameter is
    // safe. (A `remember`-cached derived value reading this same parameter was
    // exactly the bug fixed in the previous round: its factory ran once, at first
    // composition, before playback ever started.)
    val running = isPlaying || hasParticles

    val lifecycleOwner = LocalLifecycleOwner.current
    if (running) {
        LaunchedEffect(field, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Both reset per resume: `last` so returning from the background
                // does not feed in one enormous delta, `origin` so frameTimeNanos
                // starts small again instead of drifting toward the precision cliff
                // this fix exists to avoid.
                var last = 0L
                var origin = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (origin == 0L) origin = now
                        val deltaMs = if (last == 0L) 0f else (now - last) / 1_000_000f
                        last = now
                        field.update(
                            deltaMs = deltaMs,
                            isPlaying = currentIsPlaying,
                            sources = currentSources,
                            widthPx = canvasSize.width.toFloat(),
                            heightPx = canvasSize.height.toFloat(),
                            density = currentDensity
                        )
                        hasParticles = field.particles.isNotEmpty()
                        frameTimeNanos = now - origin
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
        // frameTimeNanos is read here, inside the draw lambda, and never in the
        // composable body. That defers the read to the draw phase, so each frame
        // repaints without recomposing — the old overlay recomposed sixty times a
        // second and rebuilt its config every time. The Float conversion happens
        // only here, where the magnitude is always small (an origin-relative delta,
        // never the raw uptime), so it never loses the precision that write depends on.
        drawParticles(field.particles, sprites, field.intensity, frameTimeNanos / 1_000_000f)
    }
}
