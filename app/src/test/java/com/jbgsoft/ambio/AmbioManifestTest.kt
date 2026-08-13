package com.jbgsoft.ambio

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reads the *merged* manifest — every module's contribution, as the installed app sees it —
 * and pins the two entries in it that fail silently.
 *
 * Neither is covered by any other test, and neither announces itself when it breaks. A
 * missing POST_NOTIFICATIONS declaration does not crash: Android 13+ simply never shows
 * AudioService's notification, so playback runs with no transport controls anywhere. A
 * missing tile <service> does not crash either: the tile just stops appearing in the Quick
 * Settings editor, which looks like the user never added it.
 *
 * @Config(application = Application::class) keeps Robolectric from instantiating
 * AmbioApplication, whose @HiltAndroidApp needs a Hilt test runtime this test has no other
 * use for. The manifest is read from the merged file either way. The sdk pin matches the
 * one core:common's tests already carry: Robolectric 4.16.1 tops out below this project's
 * compileSdk of 37.
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
}
