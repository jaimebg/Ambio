package com.jbgsoft.ambio.ui.layout

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaneLayoutTest {

    @Test
    fun `a phone width is compact`() {
        assertThat(paneLayoutFor(411.dp)).isEqualTo(PaneLayout.COMPACT)
    }

    @Test
    fun `a medium width stays compact because two panes do not fit`() {
        // 600-840dp is the MEDIUM band. The spec keeps it single-column
        // deliberately: it is not wide enough for two useful panes.
        assertThat(paneLayoutFor(700.dp)).isEqualTo(PaneLayout.COMPACT)
    }

    @Test
    fun `the threshold itself is expanded`() {
        assertThat(paneLayoutFor(840.dp)).isEqualTo(PaneLayout.EXPANDED)
    }

    @Test
    fun `one dp below the threshold is still compact`() {
        assertThat(paneLayoutFor(839.dp)).isEqualTo(PaneLayout.COMPACT)
    }

    @Test
    fun `a ten inch tablet is expanded`() {
        assertThat(paneLayoutFor(1280.dp)).isEqualTo(PaneLayout.EXPANDED)
    }
}
