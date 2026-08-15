package com.jbgsoft.ambio.feature.home

import android.content.Context
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The play button is the screen's primary action, so it has to be on screen
 * without scrolling at every size the app claims to support.
 *
 * It regressed once on tablet landscape: the left pane keeps the full window
 * height but only half its width, and the timer column's sizes are chosen from
 * height alone, so the full-size stack (300dp dial + presets + transport)
 * overflowed the pane and pushed the button past the bottom of its scroll area.
 */
// SDK pinned to 34: Robolectric 4.16.1 supports at most 36, and its API 36
// shadow needs Java 21 while this toolchain is 17.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransportVisibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Sizes come from the qualifiers, not from Modifier.size: a size modifier is
    // cropped against the incoming constraints and Robolectric's default device
    // is 320x470dp, so the window would never actually be this wide.
    //
    // 1280x800dp is the AVD the two-pane work was verified on, and the default
    // HomeUiState is the tallest the column ever gets: TIMER mode renders the
    // preset selector under the dial.
    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun `the play button is fully on screen in the tablet two-pane layout`() {
        assertPlayButtonUnclipped()
    }

    // The single-pane path has the same stack in a narrower, taller window; a fix
    // that only moved the breakpoint around would leave this one broken.
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h866dp")
    fun `the play button is fully on screen in the phone single-pane layout`() {
        assertPlayButtonUnclipped()
    }

    // Deliberately not covered: a landscape phone, around 410dp tall. Header,
    // toggle, transport and mix bar leave roughly 60dp for the dial there, so
    // nothing can put both the timer and the button on screen at once and the
    // window scrolls for both instead. It needs a layout of its own, not an
    // assertion. Adding one here would only pin the compromise in place.

    private fun assertPlayButtonUnclipped() {
        compose.setContent {
            HomeScreen(
                uiState = HomeUiState(effectsEnabled = false),
                onEvent = {},
                onNavigateToSettings = {},
                onNavigateToStats = {}
            )
        }

        // The merged tree is what this query hits, so the node is the button
        // itself and not the icon inside it. That matters: the icon is centred,
        // so it survives a clip that already cuts the button's edge off.
        val playButton = compose.onNodeWithContentDescription(
            context.getString(R.string.action_play)
        )

        // DpRect has no height in this version of Compose, the same gap that
        // leaves assertWidthIsAtMost missing, so measure the edges directly.
        val laidOut = playButton.getUnclippedBoundsInRoot()
        val painted = playButton.getBoundsInRoot()
        val window = compose.onRoot().getUnclippedBoundsInRoot()

        // Below the fold: laid out past the bottom of the window entirely.
        assertTrue(
            "Play button bottom is at ${laidOut.bottom}, past the window's ${window.bottom}",
            laidOut.bottom <= window.bottom
        )

        // Cut off: inside the window, but cropped by the scrolling area it sits
        // in. Clipped bounds collapse to what is actually painted, so comparing
        // the two heights catches a button that is only half drawn.
        assertEquals(
            "Play button is clipped by its scroll container",
            (laidOut.bottom - laidOut.top).value,
            (painted.bottom - painted.top).value,
            0.5f
        )
    }
}
