package com.jbgsoft.ambio.core.common.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.common.test.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The module's compileSdk/targetSdk is 37, above what Robolectric 4.16.1 (pinned in
// the version catalog) supports (max 36), and its API 36 shadow itself requires
// Java 21, which this project's toolchain (Java 17) doesn't provide. Pin the SDK
// under test to 34 rather than inheriting the manifest's targetSdk.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = AndroidStringProvider(context)

    @Test
    fun `resolves a plain string resource`() {
        assertThat(provider.get(R.string.test_plain)).isEqualTo("Plain value")
    }

    @Test
    fun `substitutes arguments into a formatted string resource`() {
        assertThat(provider.get(R.string.test_formatted, 70))
            .isEqualTo("Volume at 70 percent")
    }

    @Test
    fun `resolves the singular form of a plural resource`() {
        assertThat(provider.getQuantity(R.plurals.test_quantity, 1, 1))
            .isEqualTo("1 sound")
    }

    @Test
    fun `resolves the plural form of a plural resource`() {
        assertThat(provider.getQuantity(R.plurals.test_quantity, 3, 3))
            .isEqualTo("3 sounds")
    }
}
