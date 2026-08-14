package com.jbgsoft.ambio.feature.home.components

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.jbgsoft.ambio.core.domain.model.ActiveSound
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
class SoundBottomSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Five distinct sound names are needed so each card's contentDescription (which
    // embeds the sound's name) is unambiguous. android.R.string.* framework ids were
    // tried first, but this module's compileSdk (37) sits above what the Robolectric
    // 34 shadow actually ships: android.R.string.yes resolved to "OK" and
    // android.R.string.no resolved to "Cancel" under this classpath. Test-only string
    // resources sidestep that skew entirely.
    private fun sound(id: String, nameRes: Int, theme: SoundTheme, icon: ImageVector) = Sound(
        id = id,
        nameRes = nameRes,
        icon = icon,
        audioRes = 0,
        theme = theme
    )

    @Test
    fun `a card is disabled once the mix already holds the maximum`() {
        val sounds = listOf(
            sound("rain", TestR.string.test_sound_name, SoundTheme.RAIN, Icons.Default.WaterDrop),
            sound("fire", TestR.string.test_sound_name_2, SoundTheme.FIREPLACE, Icons.Default.LocalFireDepartment),
            sound("forest", TestR.string.test_sound_name_3, SoundTheme.FOREST, Icons.Default.Forest),
            sound("ocean", TestR.string.test_sound_name_4, SoundTheme.OCEAN, Icons.Default.Cloud),
            sound("cave", TestR.string.test_sound_name_5, SoundTheme.CAVE, Icons.Default.AcUnit)
        )
        // Three active sounds already fill the mix (MixCodec.MAX_ACTIVE_SOUNDS), so
        // the fourth (inactive) sound in the grid should render disabled.
        val activeMix = sounds.take(3).map { ActiveSound(it, 1f) }
        val fourthSoundName = context.getString(TestR.string.test_sound_name_4)
        val limitLabel = context.getString(
            com.jbgsoft.ambio.feature.home.R.string.mix_limit_reached,
            fourthSoundName
        )

        compose.setContent {
            SoundBottomSheet(
                showSheet = true,
                sounds = sounds,
                activeMix = activeMix,
                onToggleSound = {},
                onLevelChange = { _, _ -> },
                onLevelChangeFinished = {},
                onDismiss = {}
            )
        }

        compose.onNodeWithContentDescription(limitLabel).assertIsNotEnabled()
    }
}
