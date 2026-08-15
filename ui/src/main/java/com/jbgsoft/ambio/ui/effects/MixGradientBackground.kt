package com.jbgsoft.ambio.ui.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.jbgsoft.ambio.core.domain.model.MixGradient
import com.jbgsoft.ambio.ui.theme.THEME_ANIMATION_DURATION

/**
 * Where each stop's blob sits, as a fraction of the drawn size. Fixed, and the
 * same three positions for every mix: the stop count is always three, so nothing
 * ever has to move when a sound is added or removed — only its colour changes.
 * Deliberately asymmetric, so the result reads as ambient rather than as a
 * three-band chart.
 */
private val ANCHORS = listOf(
    Offset(0.12f, 0.04f),
    Offset(0.92f, 0.42f),
    Offset(0.26f, 1.00f)
)

private const val BLOB_ALPHA = 0.8f

/**
 * Blob reach, as a fraction of the larger dimension. Not transcribed from the
 * HTML mockup, whose `radial-gradient(120% 85% ..., transparent 62%)` is a
 * percentage of an ellipse and means something else entirely here; taken
 * literally each blob would cover the whole screen and the three would laminate
 * over each other until no anchor read as its own sound. 0.75 comes from the
 * actual overlap on a 9:17.5 screen: at anchor 0, blob 1 has already fallen to
 * about alpha 0.2 and blob 2 to zero.
 */
private const val BLOB_RADIUS = 0.75f

/**
 * Cross-fades a gradient towards [target], one animation per stop.
 *
 * Per-stop rather than a single animation over the whole object because the
 * stops are what change: switching a sound out of a three-mix alters one stop
 * and leaves two alone, and animating them separately keeps the other two
 * perfectly still instead of dragging them through an intermediate value.
 *
 * Hands back a [State] rather than a [MixGradient], and never reads the three
 * animations itself. A composable that returns a value has no restart scope of
 * its own, so a read here would invalidate the *caller* instead — the whole Home
 * content lambda would re-execute on every frame of the cross-fade to move a
 * colour the draw phase could have picked up on its own.
 * [mixGradientBackground] performs the read inside its cache block, which is the
 * same deferral, for the same reason, as the frameTimeNanos read in
 * AmbientEffectsOverlay.
 */
@Composable
fun animatedMixGradient(target: MixGradient): State<MixGradient> {
    val first = animateColorAsState(
        targetValue = target.stops[0],
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "gradientStop0"
    )
    val second = animateColorAsState(
        targetValue = target.stops[1],
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "gradientStop1"
    )
    val third = animateColorAsState(
        targetValue = target.stops[2],
        animationSpec = tween(THEME_ANIMATION_DURATION),
        label = "gradientStop2"
    )

    val base = target.base
    return remember(base, first, second, third) {
        derivedStateOf {
            MixGradient(base = base, stops = listOf(first.value, second.value, third.value))
        }
    }
}

/**
 * Paints the gradient behind the content: the base, then one soft radial blob
 * per stop.
 *
 * [gradient] is a lambda so that the animated colours are read where it is
 * invoked — inside the cache block, in the draw phase — and never during
 * composition. A cross-fade therefore repaints without recomposing anything.
 *
 * `drawWithCache` rather than `drawBehind`: the brushes are built once in the
 * cache block, which re-runs only when the draw size or the gradient it read
 * changes. That is every frame of the 400 ms cross-fade, which is exactly when
 * they do have to be rebuilt, and never in between.
 *
 * [panoramaIndex] and [panoramaCount] widen the composition without widening
 * the surface. The blobs are placed as though the canvas were `panoramaCount`
 * times as wide and this were slice `panoramaIndex` of it, while only the real
 * canvas is rasterised. Store screenshots use it so a set of five reads as one
 * continuous image in the Play gallery; drawing an actually five-canvas-wide
 * box instead runs past the maximum surface width and the last slices come back
 * blank. The defaults are a single full-width canvas, which is every other
 * caller.
 *
 * The distinction is not academic, because this sits underneath the particle
 * overlay. That overlay reads frameTimeNanos inside its own draw lambda and
 * nothing between it and the root isolates the invalidation, so while effects
 * run — the normal Home state — the whole display list is re-recorded every
 * frame. A `drawBehind` lambda here would be re-executed by that, and would
 * rebuild three `Brush.radialGradient`s, and with them three native
 * `android.graphics.RadialGradient`s, at refresh rate for a picture that has not
 * changed. The cached brushes keep their shaders instead.
 */
fun Modifier.mixGradientBackground(
    panoramaIndex: Int = 0,
    panoramaCount: Int = 1,
    // Last so that every existing call site keeps its trailing lambda.
    gradient: () -> MixGradient
): Modifier = drawWithCache {
    val mix = gradient()
    val base = mix.base

    val virtualWidth = size.width * panoramaCount
    val originX = -size.width * panoramaIndex

    val radius = maxOf(virtualWidth, size.height) * BLOB_RADIUS
    val blobs = ANCHORS.mapIndexed { index, anchor ->
        val color = mix.stops[index]
        Brush.radialGradient(
            // The outer stop keeps the blob's own RGB and drops only alpha.
            // Color.Transparent here would be transparent *black*, and since
            // Compose interpolates un-premultiplied, every blob would fade
            // through grey and pick up a visible halo.
            colors = listOf(color.copy(alpha = BLOB_ALPHA), color.copy(alpha = 0f)),
            center = Offset(originX + virtualWidth * anchor.x, size.height * anchor.y),
            radius = radius
        )
    }

    onDrawBehind {
        drawRect(color = base)
        blobs.forEach { drawRect(brush = it) }
    }
}
