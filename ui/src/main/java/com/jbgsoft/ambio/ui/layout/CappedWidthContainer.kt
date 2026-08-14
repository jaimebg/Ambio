package com.jbgsoft.ambio.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Centres a single content column and stops it tracking the window once the
 * window is wide enough for two panes.
 *
 * Precondition: it must wrap the whole window. The breakpoint is read from this
 * container's own width, not from the window behind it, so nesting it inside a
 * pane would measure that pane and report COMPACT for a window that is anything
 * but.
 *
 * It insets for the system bars before measuring, so the breakpoint comes from
 * usable width — the same width Home splits on, which keeps one window from
 * resolving two different layouts. Insets are consumed as they are applied, so a
 * [content] that pads for the system bars itself still pads exactly once.
 *
 * Stats and Settings share this instead of each carrying a copy of the
 * breakpoint arithmetic. [content] receives the modifier it must apply to its
 * own root — the cap cannot be applied from out here, because the child owns
 * its scroll and padding order.
 */
@Composable
fun CappedWidthContainer(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        val cap = if (paneLayoutFor(maxWidth) == PaneLayout.EXPANDED) {
            Modifier.widthIn(max = CONTENT_MAX_WIDTH)
        } else {
            // Spelled out rather than left to Modifier: a bare modifier lets the
            // column shrink-wrap, and it would only look full width for as long as
            // every child inside it happens to be fillMaxWidth.
            Modifier.fillMaxWidth()
        }
        content(Modifier.then(cap).align(Alignment.TopCenter))
    }
}
