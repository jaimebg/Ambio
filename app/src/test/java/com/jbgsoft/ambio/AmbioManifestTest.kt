package com.jbgsoft.ambio

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * Pins three manifest entries that fail silently: a missing POST_NOTIFICATIONS
 * declaration, a missing Quick Settings tile <service>, and a missing or misplaced
 * android:localeConfig attribute. None of the three is covered by any other test, and
 * none announces itself when it breaks. A missing POST_NOTIFICATIONS declaration does not
 * crash: Android 13+ simply never shows AudioService's notification, so playback runs
 * with no transport controls anywhere. A missing tile <service> does not crash either:
 * the tile just stops appearing in the Quick Settings editor, which looks like the user
 * never added it. A missing or misplaced localeConfig does not crash either: Android
 * simply never offers a per-app language picker for this app.
 *
 * The first four tests read the *merged* manifest — every module's contribution, as the
 * installed app sees it — through PackageManager. The localeConfig test reads the
 * *source* manifest directly instead; see it for why, and for what that leaves unchecked.
 *
 * @Config(application = Application::class) keeps Robolectric from instantiating
 * AmbioApplication, whose @HiltAndroidApp needs a Hilt test runtime this test has no other
 * use for. The sdk pin matches the one core:common's tests already carry: Robolectric
 * 4.16.1 tops out below this project's compileSdk of 37.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AmbioManifestTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `declares POST_NOTIFICATIONS, without which the media notification never shows`() {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()

        assertThat(declared.toList())
            .contains(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `declares the Quick Settings tile service`() {
        val services = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services
            .orEmpty()

        assertThat(services.map { it.name })
            .contains("com.jbgsoft.ambio.feature.tile.PlaybackTile")
    }

    @Test
    fun `guards the tile service behind the system-only bind permission`() {
        val tile = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services
            .orEmpty()
            .single { it.name == "com.jbgsoft.ambio.feature.tile.PlaybackTile" }

        // It is exported, so without this any app on the device could bind to it.
        assertThat(tile.permission).isEqualTo("android.permission.BIND_QUICK_SETTINGS_TILE")
    }

    @Test
    fun `points the tile at the monochrome mark, not the launcher icon`() {
        val tile = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services
            .orEmpty()
            .single { it.name == "com.jbgsoft.ambio.feature.tile.PlaybackTile" }

        // Quick Settings masks the icon to one colour and re-tints it per state. Handing it
        // the adaptive launcher icon — the easy mistake, and the one this replaced — puts a
        // full-colour raster with its own background layer in a slot built for a glyph.
        assertThat(tile.icon).isEqualTo(R.drawable.ic_tile_playback)
    }

    @Test
    fun `wires the manifest's localeConfig to the locales_config resource`() {
        // LocalesConfigTest pins the contents of locales_config.xml but never reads the
        // manifest, so removing android:localeConfig — or leaving it on the wrong element,
        // where Android silently ignores it — would leave that test green while quietly
        // taking the per-app language picker down with it. Parsing structurally, and
        // reading the attribute off the <application> element specifically, is what
        // catches the second case; a substring search on the raw text would not.
        //
        // This reads the *source* manifest, not the *merged* one the four tests above
        // read through PackageManager: Robolectric 4.16.1 does not surface this attribute
        // through ApplicationInfo the way it does permissions and services, so there is no
        // PackageManager-backed way to check it here. The gap that leaves: a manifest-merger
        // rule in some other module that stripped or overrode this attribute on the merged
        // manifest would go undetected by this test. No other module declares it today.
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        val localeConfig = application.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            "localeConfig"
        )

        assertThat(localeConfig).isEqualTo("@xml/locales_config")
    }
}
