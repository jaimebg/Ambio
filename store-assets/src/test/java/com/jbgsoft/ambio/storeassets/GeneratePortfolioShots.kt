package com.jbgsoft.ambio.storeassets

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.ui.theme.AmbioTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Stand-ins for the insets Robolectric does not provide, as in [StoreShot]. */
private val STATUS_BAR = 24.dp
private val NAV_BAR = 24.dp

/**
 * Writes bare screenshots of the real screens, for jbgsoft.com.
 *
 * The same renderer as [GenerateStoreAssets] and deliberately not the same
 * output. Play wants a marketing canvas: a caption, a tilted device, one
 * panorama sliced across the set. The site draws its own phone frame around
 * whatever it is handed, so a store image used there is a phone inside a phone,
 * carrying a caption that repeats the copy already next to it.
 *
 * These are the screen and nothing else. The canvas is the size of the device
 * rather than the size Play asks for, so there is no frame to fit into, no
 * scaling, and no backdrop around the edges — [StoreShot] and its density trick
 * are not involved at all.
 *
 * English only: the site is English. Output lands under `build/`, which is not
 * tracked, and is copied into the portfolio repo at release time.
 *
 * Run: `./gradlew :store-assets:testDebugUnitTest --tests "*GeneratePortfolioShots*"`
 * — the variant-specific task, because `test` is an aggregate and rejects
 * `--tests`.
 */
// Pinned and moded for the same reasons GenerateStoreAssets is: Robolectric
// 4.16.1 tops out below this toolchain's Java, and LEGACY graphics throws in
// Bitmap.setHasAlpha.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratePortfolioShots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * A current Pixel at 411x914dp, xxhdpi, so 1233x2742px.
     *
     * The aspect ratio is 0.450, which is what the site's phone frame is already
     * cut for — the shots it replaces were 1080x2400, or 0.450.
     */
    @Test
    @Config(qualifiers = "w411dp-h914dp-xxhdpi")
    fun phone() = renderAll(folder = "phone", widthPx = 1233, heightPx = 2742, landscape = false)

    /**
     * A ten inch tablet on its side at 1280x800dp, xhdpi, so 2560x1600px.
     *
     * The same device [ShotSpec.TEN_INCH] is cut for, and landscape for the same
     * reason: the two-pane layout is the thing worth showing, and upright this
     * tablet is under the 840dp breakpoint and renders the single column — the
     * phone shot again, at greater expense.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tablet() = renderAll(folder = "tablet", widthPx = 2560, heightPx = 1600, landscape = true)

    private fun renderAll(folder: String, widthPx: Int, heightPx: Int, landscape: Boolean) {
        // The particle field animates through withFrameNanos and never idles, so
        // a free-running clock makes waitForIdle spin. Pausing it also makes the
        // render reproducible: same commit, same frame, same pixels.
        compose.mainClock.autoAdvance = false

        val scene = mutableStateOf(StoreScene.entries.first())

        // One setContent for the whole bucket: the rule refuses a second call on
        // the same activity, so the scene is state it reads.
        compose.setContent {
            val current = scene.value

            AmbioTheme(palette = current.palette) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Opaque for the reason StoreShot's panel is: Home paints
                        // its own gradient, but the picker paints no background at
                        // all and would capture with holes in it.
                        .background(MaterialTheme.colorScheme.background)
                        // Robolectric hands the window no system bars, so
                        // systemBarsPadding() inside the app reserves nothing and
                        // every screen lays out taller than it does on a device.
                        .padding(top = STATUS_BAR, bottom = NAV_BAR)
                ) {
                    if (landscape) current.TabletMain() else current.Content()
                }
            }
        }

        StoreScene.entries.forEach { entry ->
            scene.value = entry
            // With autoAdvance off a state write does not reach composition on
            // its own, and the loop would quietly render one scene five times.
            Snapshot.sendApplyNotifications()

            // Long enough for the 400ms gradient cross-fade to land and for the
            // particles to leave their starting positions.
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.waitForIdle()

            capture(folder, entry, widthPx, heightPx)
        }
    }

    private fun capture(folder: String, scene: StoreScene, widthPx: Int, heightPx: Int) {
        val outDir = File(outputRoot, folder).apply { mkdirs() }
        val file = File(outDir, "${scene.id}.png")

        compose.runOnUiThread {
            // captureToImage() deadlocks under a paused Robolectric looper.
            // Drawing the decor view is synchronous and does not.
            val view = compose.activity.window.decorView
            assertEquals("$folder rendered at the wrong width", widthPx, view.width)
            assertEquals("$folder rendered at the wrong height", heightPx, view.height)

            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            writeOpaquePng(bitmap, file)
            bitmap.recycle()
        }

        assertTrue("${file.name} was not written", file.isFile && file.length() > 0)
    }

    /**
     * Writes a 24-bit PNG with no alpha channel.
     *
     * Bitmap.compress on an ARGB_8888 bitmap emits RGBA. Nothing rejects that
     * here the way Play does, but these are opaque screens: the alpha channel is
     * a constant 255 across every pixel and only costs bytes on the wire.
     */
    private fun writeOpaquePng(bitmap: Bitmap, file: File) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, w, h, pixels, 0, w)
        ImageIO.write(image, "png", file)
    }

    private val outputRoot: File
        get() = File(System.getProperty("ambio.portfolioShotsDir") ?: DEFAULT_OUTPUT_DIR)

    private companion object {
        // Relative to the module directory, which is where Gradle runs tests
        // from. Build output: regenerated per release, never tracked.
        const val DEFAULT_OUTPUT_DIR = "../build/portfolio-shots"
        const val SETTLE_MS = 1_200L
    }
}
