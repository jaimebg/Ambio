package com.jbgsoft.ambio.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Centres a single content column and stops it tracking the window once the
 * window is wide enough for two panes.
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cap = if (paneLayoutFor(maxWidth) == PaneLayout.EXPANDED) {
            Modifier.widthIn(max = CONTENT_MAX_WIDTH)
        } else {
            Modifier
        }
        content(Modifier.then(cap).align(Alignment.TopCenter))
    }
}
