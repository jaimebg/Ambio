package com.jbgsoft.ambio.ui.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The overlay is the one piece of this feature with nothing to assert on: the
 * field lives in a private `remember` and everything it produces goes straight
 * into a Canvas. So this test asserts on the only observable the overlay has —
 * the pixels it paints.
 *
 * It exists because of a defect that shipped past a fully green suite. The
 * frame loop used to be admitted by
 *
 *     val running by remember { derivedStateOf { isPlaying || ... } }
 *
 * and `remember` with no keys runs its factory once, so the derived state
 * permanently closed over `isPlaying` — a plain Boolean parameter, not State —
 * at its first-composition value. The overlay composes before playback starts,
 * so that value was `false`, the loop was never started, and the particle field
 * never appeared at all. Every ParticleField unit test still passed, because the
 * simulation was never the broken part; lint and the build were clean too. Only
 * the paint was different, which is why this test looks at the paint.
 *
 * SDK is pinned to 34 for the same reason as every other Robolectric test here:
 * 4.16.1 supports at most 36, and its API 36 shadow needs Java 21 while this
 * toolchain is Java 17.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmbientEffectsOverlayTest {

    // createAndroidComposeRule rather than createComposeRule because the capture
    // below needs the host window. Compose's own captureToImage() cannot be used
    // here: it posts the redraw it is waiting for to the main looper and then
    // blocks that same thread in Thread.sleep, which under Robolectric's paused
    // looper is a guaranteed deadlock — it always fails with
    // "ComposeTimeoutException: Condition still not satisfied after 2000 ms",
    // even for entirely static content. Drawing the decor view straight into a
    // Bitmap is synchronous and needs no looper turn, and @GraphicsMode(NATIVE)
    // makes it produce real Skia output rather than a blank buffer.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Time to run at each phase. Frames arrive at ~16 ms, so this is ~250 of
     * them: far past the ~80 ms the intensity ramp needs to clear ParticleField's
     * spawn gate, past the few more frames the spawn accumulator needs to reach
     * 1, and far enough up the 8000 ms ramp (to intensity 0.5) that what is
     * spawned is painted well above the renderer's MIN_DRAW_ALPHA floor.
     */
    private val phaseMs = 4_000L

    /**
     * Number of pixels the overlay painted. The content is a black box filling
     * the whole window, so before anything is drawn every pixel is pure black
     * and this is exactly zero; each particle the renderer composites over it
     * lifts some pixels off black.
     */
    private fun paintedPixelCount(): Int {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it != android.graphics.Color.BLACK }
    }

    @Test
    fun `the field paints nothing while paused and starts painting once playback starts`() {
        var playing by mutableStateOf(false)

        // Manual clock: the frame loop is an unbounded withFrameNanos loop, so an
        // auto-advancing clock would never let the composition go idle.
        compose.mainClock.autoAdvance = false

        compose.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AmbientEffectsOverlay(
                    isPlaying = playing,
                    mix = listOf(ParticleSource(SoundTheme.RAIN, 1f))
                )
            }
        }

        compose.mainClock.advanceTimeBy(phaseMs)
        assertThat(paintedPixelCount()).isEqualTo(0)

        playing = true
        // The global write observer normally flushes this on the UI dispatcher,
        // but advanceTimeBy does not run that task while autoAdvance is off, so
        // without this the recomposer never even learns isPlaying changed and
        // the test would be asserting nothing.
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(phaseMs)

        // The whole point: the loop has to start on *this* value of isPlaying,
        // not the one the overlay first composed with.
        assertThat(paintedPixelCount()).isGreaterThan(0)
    }
}
