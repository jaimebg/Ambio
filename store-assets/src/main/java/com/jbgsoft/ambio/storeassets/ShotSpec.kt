package com.jbgsoft.ambio.storeassets

/**
 * One Play Store image bucket.
 *
 * [qualifiers] is what actually decides the output size: Robolectric builds the
 * window from it, and the decor view is then exactly [widthPx] by [heightPx],
 * because width in dp multiplied by the density in the same qualifier string is
 * the pixel size Play wants. Keep the two in step.
 */
enum class ShotSpec(
    val folder: String,
    val qualifiers: String,
    val widthPx: Int,
    val heightPx: Int
) {
    /** A phone in portrait: 360x640dp at xxhdpi. Single column. */
    PHONE("phoneScreenshots", "w360dp-h640dp-xxhdpi", 1080, 1920),

    /**
     * Seven inches, landscape: 960x600dp at xhdpi.
     *
     * Landscape on purpose. A seven inch tablet held upright is about 600dp
     * wide, which is below the 840dp breakpoint, so a portrait shot of one
     * would render the single column and there would be nothing to show.
     */
    SEVEN_INCH("sevenInchScreenshots", "w960dp-h600dp-xhdpi", 1920, 1200),

    /** Ten inches, landscape: 1280x800dp at xhdpi. The size A was verified on. */
    TEN_INCH("tenInchScreenshots", "w1280dp-h800dp-xhdpi", 2560, 1600)
}
