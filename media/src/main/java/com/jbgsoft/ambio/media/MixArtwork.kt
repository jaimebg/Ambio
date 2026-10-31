package com.jbgsoft.ambio.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

/**
 * Paints a mix's gradient into the square image the session publishes as its artwork.
 *
 * The point is the system media card (#23). From Android 13 it draws the artwork as its
 * background and derives its whole colour scheme from it; with no artwork it falls back
 * to the app icon, so every mix got the same green card. Lock screens, Bluetooth heads
 * and cars show the same image.
 *
 * The same picture as the in-app background — the ground, then one soft radial blob per
 * stop — but not the same layout. The card is about twice as wide as it is tall and
 * centre-crops the square into itself, so only the middle half of the height is ever
 * seen; the app's anchors sit at the very top and bottom of a tall screen and would be
 * cropped away. These keep the app's asymmetry inside that band.
 */
internal object MixArtwork {

    /**
     * Small on purpose: the image is a blur of three colours and loses nothing to
     * upscaling, and these bytes cross to every controller whenever the mix changes.
     */
    const val SIZE_PX = 256

    /** One blob per stop, as fractions of the side. All inside the band a card keeps. */
    val ANCHORS: List<Pair<Float, Float>> = listOf(
        0.14f to 0.30f,
        0.88f to 0.44f,
        0.40f to 0.72f
    )

    private const val BLOB_ALPHA = 0.8f

    /**
     * Blob reach as a fraction of the side. Smaller than the app's 0.75, which is
     * measured against the long side of a tall screen; on a square that would wash all
     * three blobs into one another.
     */
    private const val BLOB_RADIUS = 0.6f

    fun encode(colors: MixColors): ByteArray {
        val bitmap = createBitmap(SIZE_PX, SIZE_PX)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(colors.base)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            val side = SIZE_PX.toFloat()
            ANCHORS.forEachIndexed { index, (x, y) ->
                val color = colors.stops[index]
                paint.shader = RadialGradient(
                    side * x,
                    side * y,
                    side * BLOB_RADIUS,
                    // The outer stop keeps the blob's own RGB and drops only alpha;
                    // fading to transparent black would ring every blob in grey.
                    ColorUtils.setAlphaComponent(color, (BLOB_ALPHA * 255).toInt()),
                    ColorUtils.setAlphaComponent(color, 0),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, side, side, paint)
            }

            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
