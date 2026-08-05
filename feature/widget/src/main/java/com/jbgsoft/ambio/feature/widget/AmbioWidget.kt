package com.jbgsoft.ambio.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.glance.layout.Spacer

/**
 * A 4x1 widget: the mix's name, and one button.
 *
 * It renders a [WidgetDisplay] and decides nothing itself — every question of what it says
 * or what colour it is was already answered by [widgetDisplay], where it is testable.
 */
object AmbioWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val display = WidgetUpdater.currentDisplay(context)
        provideContent { Content(context, display) }
    }

    @Composable
    private fun Content(context: Context, display: WidgetDisplay) {
        val onBackground = ColorProvider(display.palette.onPrimary)
        GlanceTheme {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(display.palette.surface))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 16.dp)
                    .clickable(actionSendBroadcast(playPauseIntent(context))),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = display.title,
                    style = TextStyle(
                        color = ColorProvider(display.palette.primary),
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = context.getString(
                        if (display.isPlaying) R.string.widget_pause else R.string.widget_play
                    ),
                    style = TextStyle(color = onBackground, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
