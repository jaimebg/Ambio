package com.jbgsoft.ambio.feature.stats

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the stateless body renders from plain state and reports back navigation`() {
        var wentBack = false
        compose.setContent {
            StatsScreen(
                uiState = StatsUiState(totalFocusMinutes = 75, completedSessionCount = 3),
                onDeleteSession = {},
                onNavigateBack = { wentBack = true }
            )
        }

        compose.onNodeWithContentDescription(
            context.getString(R.string.stats_back)
        ).performClick()

        assertThat(wentBack).isTrue()
    }
}
