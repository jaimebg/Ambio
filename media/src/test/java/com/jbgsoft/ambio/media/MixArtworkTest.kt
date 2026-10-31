package com.jbgsoft.ambio.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session artwork is what the system media card takes its colours from (#23): with
 * none, Android 13+ falls back to the app icon, so every mix gets the same card.
 *
 * Rendered with real Skia (NATIVE graphics) because the claims are about pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MixArtworkTest {

    // SoundGlow's RAIN, FIREPLACE and FOREST over GRADIENT_BASE (core:domain), chosen
    // so the dominant channel differs at each anchor: blue, red, green.
    private val base = 0xFF0C0E12.toInt()
    private val rain = 0xFF181D3E.toInt()
    private val fireplace = 0xFF582A1F.toInt()
    private val forest = 0xFF0D472F.toInt()

    private fun render(colors: MixColors): Bitmap {
        val bytes = MixArtwork.encode(colors)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Test
    fun `the artwork is a square image`() {
        val bitmap = render(MixColors(base, listOf(rain, fireplace, forest)))

        assertThat(bitmap.width).isEqualTo(MixArtwork.SIZE_PX)
        assertThat(bitmap.height).isEqualTo(MixArtwork.SIZE_PX)
    }

    @Test
    fun `each stop dominates its own anchor`() {
        val bitmap = render(MixColors(base, listOf(rain, fireplace, forest)))

        fun pixelAt(index: Int): Int {
            val (x, y) = MixArtwork.ANCHORS[index]
            return bitmap.getPixel(
                ((bitmap.width - 1) * x).toInt(),
                ((bitmap.height - 1) * y).toInt()
            )
        }

        val p0 = pixelAt(0)
        assertWithMessage("anchor 0 should read blue, was #%s", Integer.toHexString(p0))
            .that(Color.blue(p0) > Color.red(p0) && Color.blue(p0) > Color.green(p0)).isTrue()
        val p1 = pixelAt(1)
        assertWithMessage("anchor 1 should read red, was #%s", Integer.toHexString(p1))
            .that(Color.red(p1) > Color.green(p1) && Color.red(p1) > Color.blue(p1)).isTrue()
        val p2 = pixelAt(2)
        assertWithMessage("anchor 2 should read green, was #%s", Integer.toHexString(p2))
            .that(Color.green(p2) > Color.red(p2) && Color.green(p2) > Color.blue(p2)).isTrue()
    }

    @Test
    fun `every anchor survives the crop of a wide media card`() {
        // The card is about twice as wide as it is tall and centre-crops the artwork
        // into it, so only the middle half of a square's height is ever shown. An
        // anchor outside that band paints a sound the card cannot show.
        MixArtwork.ANCHORS.forEach { (_, y) ->
            assertThat(y).isAtLeast(0.25f)
            assertThat(y).isAtMost(0.75f)
        }
    }
}
