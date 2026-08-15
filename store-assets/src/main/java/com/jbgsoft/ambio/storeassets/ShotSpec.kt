package com.jbgsoft.ambio.storeassets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One Play Store image bucket.
 *
 * Two sizes matter here and they are not the same thing.
 *
 * [canvasQualifiers] sizes the image Play receives: Robolectric builds the
 * window from it, and the decor view is then exactly [widthPx] by [heightPx],
 * because the width in dp multiplied by the density in the same string is the
 * pixel size. Keep the two in step.
 *
 * [deviceWidth] and [deviceHeight] are the screen the app believes it is running
 * on. The app is laid out at that size and the result is scaled to fit the
 * canvas, rather than being squeezed into whatever the frame has left over.
 * Squeezing is what made the first set look wrong: at 360x640dp minus a caption
 * the column fell under the 500dp threshold, so the app switched to its
 * very-small-screen sizing and the dial collided with its own label. These are
 * marketing images of real phones, not of a 2013 handset.
 *
 * Play's requirements, checked rather than assumed:
 * - JPEG or 24-bit PNG with no alpha
 * - between 320px and 3840px a side, and no side more than twice the other
 * - phones: 9:16 portrait, at least 1080x1920
 * - tablets: 16:9 landscape, uploaded between 1080px and 7680px
 */
enum class ShotSpec(
    val folder: String,
    val canvasQualifiers: String,
    val widthPx: Int,
    val heightPx: Int,
    val deviceWidth: Dp,
    val deviceHeight: Dp
) {
    /**
     * Phone, 9:16 portrait at Play's recommended 1080x1920.
     *
     * The device is 411x914dp, a current Pixel. Comfortably past the 600dp
     * threshold, so the app renders at full size the way most phones see it.
     */
    PHONE(
        folder = "phoneScreenshots",
        canvasQualifiers = "w360dp-h640dp-xxhdpi",
        widthPx = 1080,
        heightPx = 1920,
        deviceWidth = 411.dp,
        deviceHeight = 914.dp
    ),

    /**
     * Seven inches, 16:9 landscape at 1920x1080.
     *
     * The device is 960x600dp, a seven inch tablet turned on its side. Landscape
     * on purpose: upright it is about 600dp wide, under the 840dp breakpoint, so
     * a portrait shot would render the single column and have no two panes to
     * show.
     */
    SEVEN_INCH(
        folder = "sevenInchScreenshots",
        canvasQualifiers = "w960dp-h540dp-xhdpi",
        widthPx = 1920,
        heightPx = 1080,
        deviceWidth = 960.dp,
        deviceHeight = 600.dp
    ),

    /** Ten inches, 16:9 landscape at 2560x1440. The device is the 1280x800dp AVD A was verified on. */
    TEN_INCH(
        folder = "tenInchScreenshots",
        canvasQualifiers = "w1280dp-h720dp-xhdpi",
        widthPx = 2560,
        heightPx = 1440,
        deviceWidth = 1280.dp,
        deviceHeight = 800.dp
    )
}
