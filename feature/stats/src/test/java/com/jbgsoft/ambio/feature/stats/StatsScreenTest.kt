package com.jbgsoft.ambio.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.ui.layout.CONTENT_MAX_WIDTH
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

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `at expanded width the content stops tracking the window and caps its column`() {
        compose.setContent {
            StatsScreen(
                uiState = StatsUiState(totalFocusMinutes = 75, completedSessionCount = 3),
                onDeleteSession = {},
                onNavigateBack = {}
            )
        }

        // The header Row is fillMaxWidth, so uncapped it would span all 1280dp.
        // androidx.compose.ui.test has no assertWidthIsAtMost, so the bound is
        // read directly and compared.
        val headerWidth = compose.onNodeWithTag("statsHeader").getUnclippedBoundsInRoot().width
        assertThat(headerWidth <= CONTENT_MAX_WIDTH).isTrue()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `at compact width the content still spans the window`() {
        compose.setContent {
            StatsScreen(
                uiState = StatsUiState(),
                onDeleteSession = {},
                onNavigateBack = {}
            )
        }

        // Guards the other direction: the cap must not leak into phone widths.
        compose.onNodeWithTag("statsHeader").assertWidthIsAtLeast(400.dp)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w700dp-h800dp")
    fun `at a compact width wider than the cap the content still spans the window`() {
        compose.setContent {
            StatsScreen(
                uiState = StatsUiState(),
                onDeleteSession = {},
                onNavigateBack = {}
            )
        }

        // 700dp is still COMPACT (under the 840dp threshold) but wider than
        // CONTENT_MAX_WIDTH (600dp) — the 411dp check above is narrower than
        // the cap itself, so it cannot tell "no cap" from "cap always
        // applied" apart. This width can.
        val headerWidth = compose.onNodeWithTag("statsHeader").getUnclippedBoundsInRoot().width
        assertThat(headerWidth > CONTENT_MAX_WIDTH).isTrue()
    }
}
