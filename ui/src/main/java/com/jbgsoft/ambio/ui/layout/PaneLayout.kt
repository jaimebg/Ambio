package com.jbgsoft.ambio.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which layout a screen should use at a given width.
 *
 * Derived from [androidx.compose.foundation.layout.BoxWithConstraints] rather than
 * `WindowSizeClass` on purpose: `calculateWindowSizeClass` needs an Activity, which
 * neither Robolectric tests nor the store-asset renderer have. 840.dp is Material3's
 * EXPANDED boundary, so screens land on the same breakpoint the spec names.
 */
enum class PaneLayout { COMPACT, EXPANDED }

/** Material3's EXPANDED boundary. Below this, two panes have no room to be useful. */
val EXPANDED_WIDTH_THRESHOLD: Dp = 840.dp

/** How wide a single content column is allowed to grow before it stops tracking width. */
val CONTENT_MAX_WIDTH: Dp = 600.dp

fun paneLayoutFor(width: Dp): PaneLayout =
    if (width >= EXPANDED_WIDTH_THRESHOLD) PaneLayout.EXPANDED else PaneLayout.COMPACT
