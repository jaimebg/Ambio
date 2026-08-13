package com.jbgsoft.ambio.feature.home.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.feature.home.test.R as TestR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK pinned to 34 for the same reason as StringProviderTest: Robolectric 4.16.1
// supports at most 36, and its API 36 shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val sound = Sound(
        id = "rain",
        nameRes = TestR.string.test_sound_name,
        icon = Icons.Default.WaterDrop,
        audioRes = 0,
        illustrationRes = 0,
        theme = SoundTheme.RAIN
    )

    private fun render(isActive: Boolean, canDeactivate: Boolean, canActivate: Boolean) {
        compose.setContent {
            SoundCard(
                sound = sound,
                isActive = isActive,
                level = 1f,
                canDeactivate = canDeactivate,
                canActivate = canActivate,
                onToggle = {},
                onLevelChange = {},
                onLevelChangeFinished = {}
            )
        }
    }

    // createComposeRule() has no `activity` property (that is createAndroidComposeRule
    // only), so strings are resolved the same way StringProviderTest does.
    private fun label(id: Int) = context.getString(id, "Rain")

    @Test
    fun `an inactive card is disabled once the mix is full`() {
        render(isActive = false, canDeactivate = true, canActivate = false)

        compose.onNodeWithContentDescription(label(com.jbgsoft.ambio.feature.home.R.string.mix_limit_reached))
            .assertIsNotEnabled()
    }

    @Test
    fun `an inactive card is enabled while the mix has room`() {
        render(isActive = false, canDeactivate = true, canActivate = true)

        compose.onNodeWithContentDescription(label(com.jbgsoft.ambio.feature.home.R.string.mix_add_sound))
            .assertIsEnabled()
    }

    @Test
    fun `the last active card is disabled, because the mix is never empty`() {
        render(isActive = true, canDeactivate = false, canActivate = false)

        compose.onNodeWithContentDescription(label(com.jbgsoft.ambio.feature.home.R.string.mix_remove_sound))
            .assertIsNotEnabled()
    }

    @Test
    fun `an active card in a full mix can still be removed`() {
        render(isActive = true, canDeactivate = true, canActivate = false)

        compose.onNodeWithContentDescription(label(com.jbgsoft.ambio.feature.home.R.string.mix_remove_sound))
            .assertIsEnabled()
    }
}
