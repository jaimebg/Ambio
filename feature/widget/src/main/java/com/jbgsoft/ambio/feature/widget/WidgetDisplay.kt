package com.jbgsoft.ambio.feature.widget

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AmbioPalette
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.mixPalettes

/** Everything the widget draws, and nothing about how it is drawn. */
data class WidgetDisplay(
    val title: String,
    val palette: AmbioPalette,
    val isPlaying: Boolean
)

/**
 * The widget's only logic, deliberately kept as a pure function.
 *
 * This project has no instrumented tests, so nothing will ever check that the widget
 * paints. Keeping the decisions here — what it says, what colour it is — means the part
 * that can be wrong is the part that is covered.
 *
 * [names] and [countLabel] are passed in rather than resolved here so this stays free of
 * Android: the callers hand it StringProvider or stringResource.
 */
fun widgetDisplay(
    mix: List<ActiveSound>,
    isPlaying: Boolean,
    names: (Int) -> String,
    countLabel: (Int) -> String
): WidgetDisplay {
    // A widget renders before DataStore has been read on a cold start, and mixPalettes
    // rejects an empty list. A widget that throws takes the launcher's rendering with it.
    if (mix.isEmpty()) {
        return WidgetDisplay(
            title = "",
            palette = mixPalettes(listOf(SoundTheme.RAIN)),
            isPlaying = isPlaying
        )
    }

    // The same rule as HomeViewModel.mixTitle and the media notification, so all three
    // surfaces say the same thing.
    val title = if (mix.size <= 2) {
        mix.joinToString(" + ") { names(it.sound.nameRes) }
    } else {
        countLabel(mix.size)
    }

    return WidgetDisplay(
        title = title,
        palette = mixPalettes(mix.map { it.sound.theme }),
        isPlaying = isPlaying
    )
}
