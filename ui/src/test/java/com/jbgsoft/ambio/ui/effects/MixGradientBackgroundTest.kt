package com.jbgsoft.ambio.ui.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertWithMessage
import com.jbgsoft.ambio.core.domain.model.MixGradient
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.gradientOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The modifier's only observable is the pixels it paints, so that is what this
 * looks at — the same reasoning, and the same capture technique, as
 * AmbientEffectsOverlayTest.
 *
 * It asserts on which channel dominates near each anchor rather than on exact
 * colours. The blobs are translucent and do overlap, so an exact expected value
 * would encode the compositing order and break on any tuning of alpha or radius;
 * "the blue sound's corner is blue" is the property that actually matters and it
 * survives that tuning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MixGradientBackgroundTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(gradient: MixGradient) {
        compose.setContent {
            Box(Modifier.fillMaxSize().mixGradientBackground(gradient))
        }
        compose.waitForIdle()
    }

    /** The painted colour at a fractional position in the window. */
    private fun pixelAt(xFraction: Float, yFraction: Float): Triple<Int, Int, Int> {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val x = ((view.width - 1) * xFraction).toInt().coerceIn(0, view.width - 1)
        val y = ((view.height - 1) * yFraction).toInt().coerceIn(0, view.height - 1)
        val pixel = bitmap.getPixel(x, y)
        return Triple(
            android.graphics.Color.red(pixel),
            android.graphics.Color.green(pixel),
            android.graphics.Color.blue(pixel)
        )
    }

    @Test
    fun `each sound dominates its own anchor`() {
        // RAIN is blue, FIREPLACE orange-red, FOREST green — chosen so the
        // dominant channel differs at each of the three anchors.
        render(gradientOf(listOf(SoundGlow.RAIN, SoundGlow.FIREPLACE, SoundGlow.FOREST)))

        val (r0, g0, b0) = pixelAt(0.12f, 0.04f)
        assertWithMessage("anchor 0 should read blue, was ($r0, $g0, $b0)")
            .that(b0 > r0 && b0 > g0).isTrue()

        val (r1, g1, b1) = pixelAt(0.92f, 0.42f)
        assertWithMessage("anchor 1 should read red, was ($r1, $g1, $b1)")
            .that(r1 > g1 && r1 > b1).isTrue()

        val (r2, g2, b2) = pixelAt(0.26f, 0.99f)
        assertWithMessage("anchor 2 should read green, was ($r2, $g2, $b2)")
            .that(g2 > r2 && g2 > b2).isTrue()
    }

    @Test
    fun `one sound paints one colour across all three anchors`() {
        render(gradientOf(listOf(SoundGlow.RAIN)))

        listOf(0.12f to 0.04f, 0.92f to 0.42f, 0.26f to 0.99f).forEach { (x, y) ->
            val (r, g, b) = pixelAt(x, y)
            assertWithMessage("($x, $y) should read blue, was ($r, $g, $b)")
                .that(b > g && g >= r).isTrue()
        }
    }

    @Test
    fun `blobs do not fade through grey`() {
        // Regression guard for fading to Color.Transparent instead of
        // colour.copy(alpha = 0f). Compose lerps un-premultiplied, so the wrong
        // one drags every blob's edge toward transparent black and washes the
        // hue out. Sampled midway down the falloff, well off any anchor.
        render(gradientOf(listOf(SoundGlow.FOREST)))

        val (r, g, b) = pixelAt(0.55f, 0.5f)
        assertWithMessage("mid-falloff pixel lost its hue: ($r, $g, $b)")
            .that(g > r && g > b).isTrue()
    }
}
