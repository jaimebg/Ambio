package com.jbgsoft.ambio.feature.tile

import android.content.Context
import android.content.SharedPreferences

/**
 * The persisted "is playback active" flag the Quick Settings tile reads.
 *
 * It exists because the tile is a separate, ephemeral process from AudioService: it cannot
 * hold the answer in memory, so AudioService's playback broadcast (via
 * [PlaybackStateReceiver]) and [PlaybackTile] itself both go through this shared flag instead.
 */
object PlaybackFlag {

    private const val PREFS = "ambio_playback"
    private const val KEY_PLAYING = "is_playing"

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
     * A stale `true` is worse than a wrong label: the tile shows Active over a service
     * that no longer exists, so tapping it does the opposite of what it shows — the user
     * taps to stop and playback *starts*.
     *
     * A fresh app process is proof the flag is false: AudioService is declared without
     * android:process, so it lives in this same process and cannot have survived it. And
     * Application.onCreate runs before any activity, service or receiver in the process,
     * so this cannot race the playback broadcast that would legitimately set it true.
     */
    fun clearPlaying(context: Context) = writePlaying(context, false)

    fun setPlaying(context: Context, playing: Boolean) = writePlaying(context, playing)

    /** The one write, so clearPlaying and setPlaying cannot drift apart. */
    private fun writePlaying(context: Context, playing: Boolean) {
        prefs(context).edit().putBoolean(KEY_PLAYING, playing).apply()
    }
}
