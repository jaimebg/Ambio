package com.jbgsoft.ambio.feature.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.jbgsoft.ambio.core.common.resources.StringProvider
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * The single place anything says "the widget is out of date".
 *
 * Two callers, each covering what only it knows: AudioService when playback starts or stops,
 * and the app when the mix changes. The service cannot own the second — pushMix() does not
 * reach it when the controller is null, so a mix edited while the service is dead would never
 * arrive. The mix is only ever written by the app's UI, so the app is always alive when it
 * changes.
 */
object WidgetUpdater {

    private const val PREFS = "ambio_widget"
    private const val KEY_PLAYING = "is_playing"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun soundRepository(): SoundRepository
        fun stringProvider(): StringProvider
    }

    // GlanceAppWidget is not an Android component, so Hilt cannot inject it. This is the
    // documented way in: reach the singleton graph through the Context it is handed.
    private fun entryPoint(context: Context): WidgetEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Defaults to false, which is what a fresh boot with no service should show. */
    fun isPlaying(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PLAYING, false)

    /**
     * Forces the flag back to false, for Application.onCreate to call.
     *
     * AudioService.onDestroy clears it on an orderly stop, but the low-memory killer, a
     * crash and force-stop all skip onDestroy, and nothing else would ever reconcile it.
     * A stale `true` is worse than a wrong label: the widget shows Pause over a service
     * that no longer exists, so its button does the opposite of what it says — the user
     * taps Pause and playback *starts*.
     *
     * A fresh app process is proof the flag is false: AudioService is declared without
     * android:process, so it lives in this same process and cannot have survived it. And
     * Application.onCreate runs before any activity, service or receiver in the process,
     * so this cannot race the playback broadcast that would legitimately set it true.
     *
     * Not suspend, and deliberately no refresh(): onCreate has no scope to suspend in, and
     * the two things that start a process here re-render anyway — a widget update calls
     * currentDisplay(), and opening the app hits the collector in AmbioApp.
     */
    fun clearPlaying(context: Context) = writePlaying(context, false)

    suspend fun setPlaying(context: Context, playing: Boolean) {
        writePlaying(context, playing)
        refresh(context)
    }

    /** The one write, so clearPlaying and setPlaying cannot drift apart. */
    private fun writePlaying(context: Context, playing: Boolean) {
        prefs(context).edit().putBoolean(KEY_PLAYING, playing).apply()
    }

    suspend fun currentDisplay(context: Context): WidgetDisplay {
        val ep = entryPoint(context)
        val mix = ep.soundRepository().getActiveMix().first()
        val strings = ep.stringProvider()
        return widgetDisplay(
            mix = mix,
            isPlaying = isPlaying(context),
            names = { strings.get(it) },
            countLabel = { strings.get(R.string.widget_sound_count, it) }
        )
    }

    suspend fun refresh(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(AmbioWidget::class.java).forEach { id ->
            AmbioWidget.update(context, id)
        }
    }
}
