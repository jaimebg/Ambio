package com.jbgsoft.ambio.storeassets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.jbgsoft.ambio.ui.theme.AmbioTheme
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
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
 * A locale Play lists this app in, paired with the language tag that resolves to
 * the matching `values-*` directory.
 *
 * The two are not the same string, and the differences are the whole reason this
 * table is written out rather than derived. Play says `no-NO` where Android
 * wants Norwegian Bokmal, `id` and `iw-IL` land on Java's frozen `in` and `iw`,
 * and Chinese resolves by script rather than by region.
 */
private data class ShotLocale(val play: String, val tag: String)

private val LOCALES = listOf(
    ShotLocale("af", "af"),
    ShotLocale("ar", "ar"),
    ShotLocale("bg", "bg"),
    ShotLocale("bn-BD", "bn-BD"),
    ShotLocale("ca", "ca"),
    ShotLocale("cs-CZ", "cs"),
    ShotLocale("da-DK", "da"),
    ShotLocale("de-DE", "de"),
    ShotLocale("el-GR", "el"),
    ShotLocale("en-GB", "en-GB"),
    ShotLocale("en-US", "en-US"),
    ShotLocale("es-419", "es-419"),
    ShotLocale("es-ES", "es-ES"),
    ShotLocale("et", "et"),
    ShotLocale("fi-FI", "fi"),
    ShotLocale("fil", "fil"),
    ShotLocale("fr-CA", "fr-CA"),
    ShotLocale("fr-FR", "fr"),
    ShotLocale("hi-IN", "hi"),
    ShotLocale("hr", "hr"),
    ShotLocale("hu-HU", "hu"),
    ShotLocale("id", "id"),
    ShotLocale("it-IT", "it"),
    ShotLocale("iw-IL", "he"),
    ShotLocale("ja-JP", "ja"),
    ShotLocale("ko-KR", "ko"),
    ShotLocale("lt", "lt"),
    ShotLocale("lv", "lv"),
    ShotLocale("ms", "ms"),
    ShotLocale("nl-NL", "nl"),
    ShotLocale("no-NO", "nb"),
    ShotLocale("pl-PL", "pl"),
    ShotLocale("pt-BR", "pt-BR"),
    ShotLocale("pt-PT", "pt-PT"),
    ShotLocale("ro", "ro"),
    ShotLocale("ru-RU", "ru"),
    ShotLocale("sk", "sk"),
    ShotLocale("sl", "sl"),
    ShotLocale("sr", "sr"),
    ShotLocale("sv-SE", "sv"),
    ShotLocale("sw", "sw"),
    ShotLocale("th", "th"),
    ShotLocale("tr-TR", "tr"),
    ShotLocale("uk", "uk"),
    ShotLocale("vi", "vi"),
    ShotLocale("zh-CN", "zh-Hans-CN"),
    ShotLocale("zh-HK", "zh-Hant-HK"),
    ShotLocale("zh-TW", "zh-Hant-TW")
)

/**
 * Writes the Play Store screenshots by rendering the real screens on the JVM.
 *
 * No emulator, consistent with this project's standing rule that instrumented
 * tests are local tooling. Images land in
 * `fastlane/metadata/android/<locale>/images/<bucket>/`, where supply expects
 * them. They are build output: regenerated per release, and `fastlane/` is not
 * tracked.
 *
 * All 48 locales render in one pass per bucket. The locale reaches the
 * composition as a context built from a Configuration rather than through
 * Robolectric's qualifiers, because the rule creates the activity before any
 * test code runs and a qualifier set afterwards would not reach the resources it
 * has already resolved.
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

        val landscape = spec.widthPx > spec.heightPx
        val scene = mutableStateOf(StoreScene.entries.first())
        val locale = mutableStateOf(LOCALES.first())

        // One setContent for the whole bucket: the rule refuses a second call on
        // the same activity, so both the scene and the locale are state it reads.
        compose.setContent {
            val current = scene.value
            val context = localizedContext(compose.activity, locale.value.tag)

            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides layoutDirectionOf(locale.value.tag)
            ) {
                AmbioTheme(palette = current.palette) {
                    StoreShot(
                        caption = stringResource(current.captionRes),
                        glows = current.glows,
                        spec = spec,
                        index = current.ordinal,
                        total = StoreScene.entries.size
                    ) {
                        // Landscape leads with the tablet layout, portrait with
                        // the phone one, so each canvas shows what it is selling.
                        if (landscape) current.TabletMain() else current.Content()
                    }
                }
            }
        }

        LOCALES.forEach { shotLocale ->
            locale.value = shotLocale
            StoreScene.entries.forEachIndexed { index, entry ->
                scene.value = entry
                // With autoAdvance off, a state write does not reach composition
                // on its own. Without this the loop would quietly render one
                // scene over and over, which looks exactly like a working run.
                Snapshot.sendApplyNotifications()

                // Long enough for the 400ms gradient cross-fade to land and for
                // the particles to spread out of their starting positions.
                compose.mainClock.advanceTimeBy(SETTLE_MS)
                compose.waitForIdle()

                capture(spec, shotLocale.play, entry, index)
            }
        }
    }

    private fun capture(spec: ShotSpec, playLocale: String, scene: StoreScene, index: Int) {
        val outDir = File(outputRoot, "$playLocale/images/${spec.folder}").apply { mkdirs() }
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

    private fun localizedContext(base: Context, tag: String): Context {
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    /** Arabic and Hebrew mirror; nothing else in this catalogue of locales does. */
    private fun layoutDirectionOf(tag: String): LayoutDirection {
        val language = Locale.forLanguageTag(tag).language
        return if (language == "ar" || language == "iw" || language == "he") {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    private val outputRoot: File
        get() = File(System.getProperty("ambio.storeMetadataDir") ?: DEFAULT_METADATA_DIR)

    private companion object {
        // Relative to the module directory, which is where Gradle runs tests from.
        const val DEFAULT_METADATA_DIR = "../fastlane/metadata/android"
        const val SETTLE_MS = 1_200L
    }
}
