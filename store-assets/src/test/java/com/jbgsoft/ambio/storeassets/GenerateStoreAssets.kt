package com.jbgsoft.ambio.storeassets

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/**
 * Writes the Play Store screenshots by rendering the real screens on the JVM.
 *
 * No emulator, consistent with this project's standing rule that instrumented
 * tests are local tooling. Images land in
 * `fastlane/metadata/android/<locale>/images/<bucket>/`, where supply expects
 * them. They are build output: regenerated per release, and `fastlane/` is not
 * tracked.
 *
 * Locale comes from `-Dambio.shotLocale`, so the fan-out across all 48 is a loop
 * outside Gradle rather than 48 copies of this class.
 */
// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// NATIVE, not LEGACY: LEGACY throws in Bitmap.setHasAlpha, and measures CJK
// glyphs at almost zero width, which would silently wreck the Japanese and
// Chinese shots.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateStoreAssets {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun phone() = renderAll(ShotSpec.PHONE)

    @Test
    @Config(qualifiers = "w960dp-h540dp-xhdpi")
    fun sevenInch() = renderAll(ShotSpec.SEVEN_INCH)

    @Test
    @Config(qualifiers = "w1280dp-h720dp-xhdpi")
    fun tenInch() = renderAll(ShotSpec.TEN_INCH)

    private fun renderAll(spec: ShotSpec) {
        // The particle field animates through withFrameNanos and never idles, so
        // with the clock free-running waitForIdle spins and Robolectric's
        // ShadowTrace grows a trace section per frame until the heap gives out.
        // Pausing the clock also makes the render reproducible: same commit,
        // same frame, same pixels.
        compose.mainClock.autoAdvance = false

        // One setContent for the whole bucket, with the scene held in state: the
        // rule refuses a second call on the same activity.
        val scene = mutableStateOf(StoreScene.entries.first())
        val shotSpec = spec
        val landscape = spec.widthPx > spec.heightPx
        compose.setContent {
            val current = scene.value
            AmbioTheme(palette = current.palette) {
                StoreShot(
                    caption = stringResource(current.captionRes),
                    glows = current.glows,
                    spec = shotSpec,
                    index = current.ordinal,
                    total = StoreScene.entries.size
                ) {
                    // Landscape leads with the tablet layout, portrait with the
                    // phone one, so each canvas shows what it is actually selling.
                    if (landscape) current.TabletMain() else current.Content()
                }
            }
        }

        StoreScene.entries.forEachIndexed { index, entry ->
            scene.value = entry
            // With autoAdvance off, a state write does not reach composition on
            // its own. Without this the loop would quietly render scene one five
            // times over, which looks exactly like a working run.
            Snapshot.sendApplyNotifications()

            // Long enough for the 400ms gradient cross-fade to land and for the
            // particles to spread out of their starting positions.
            compose.mainClock.advanceTimeBy(SETTLE_MS)
            compose.waitForIdle()

            capture(spec, entry, index)
        }
    }

    private fun capture(spec: ShotSpec, scene: StoreScene, index: Int) {
        val outDir = File(outputRoot, "${locale()}/images/${spec.folder}").apply { mkdirs() }
        // Play orders screenshots by filename, so the index has to lead.
        val file = File(outDir, "${index + 1}_${scene.id}.png")

        compose.runOnUiThread {
            // captureToImage() deadlocks under a paused Robolectric looper: it
            // posts to the handler and then sleeps on the very thread it posted
            // to. Drawing the decor view is synchronous and does not.
            val view = compose.activity.window.decorView
            assertEquals(
                "${spec.name} rendered at the wrong width for Play",
                spec.widthPx,
                view.width
            )
            assertEquals(
                "${spec.name} rendered at the wrong height for Play",
                spec.heightPx,
                view.height
            )

            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            writeOpaquePng(bitmap, file)
            bitmap.recycle()
        }

        assertTrue("${file.name} was not written", file.isFile && file.length() > 0)
    }

    /**
     * Writes a 24-bit PNG with no alpha channel, which is what Play accepts.
     *
     * Bitmap.compress on an ARGB_8888 bitmap emits RGBA, and an alpha channel is
     * grounds for rejection. TYPE_INT_RGB drops it. ImageIO is available because
     * all of this runs on the JVM.
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

    private fun locale(): String = System.getProperty("ambio.shotLocale") ?: "en-US"

    private val outputRoot: File
        get() = File(System.getProperty("ambio.storeMetadataDir") ?: DEFAULT_METADATA_DIR)

    private companion object {
        // Relative to the module directory, which is where Gradle runs tests from.
        const val DEFAULT_METADATA_DIR = "../fastlane/metadata/android"
        const val SETTLE_MS = 1_200L
    }
}
