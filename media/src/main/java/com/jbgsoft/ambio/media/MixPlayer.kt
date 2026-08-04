package com.jbgsoft.ambio.media

import android.os.Looper
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Presents N simultaneously playing sounds to the MediaSession as one logical
 * playback.
 *
 * It deliberately delegates to none of the underlying tracks. Designating one as a
 * leader and forwarding to it is less code, but the leader stops existing when the
 * user removes that sound from the mix, and a MediaSession's player cannot be
 * swapped after it is built — reassigning it would restart a sound the user did
 * not touch.
 *
 * SimpleBasePlayer, State and MediaItemData are all @UnstableApi in Media3 1.10.1;
 * this class is opted in wholesale rather than at each call site.
 */
@UnstableApi
class MixPlayer(
    looper: Looper,
    private val createTrack: (soundId: String) -> SoundTrack
) : SimpleBasePlayer(looper) {

    private class Entry(val track: SoundTrack, var level: Float)

    private val entries = LinkedHashMap<String, Entry>()
    private var playWhenReadyValue = false
    private var masterVolume = 1f
    private var title = ""

    // SimpleBasePlayer's own `released` flag is private, so the standard Player
    // methods' !released guard (see shouldHandleCommand()) doesn't cover these three
    // methods below, which are MixPlayer's own API rather than overrides of Player.
    // Track it ourselves and make all three no-ops afterward, so a call arriving after
    // release() can never create or start a track that nothing can ever stop again.
    private var released = false

    private val commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_RELEASE,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE
        )
        .build()

    // --- the mixer's own API, reached through custom session commands ---

    /**
     * Makes the mix exactly [mix]: sounds no longer present are released, missing ones
     * are started, and every level plus the title are re-asserted.
     *
     * This is the only entry point the session exposes, and it is idempotent by
     * construction. handleStop() releases every track, and the service can be killed
     * and rebound at any time; a caller that only sent deltas would have no way to
     * rebuild that state, and no way to know it had drifted. Sending the whole mix
     * means "re-send it" is always a valid repair, so the caller never has to model
     * what the service currently holds.
     */
    fun setMix(mix: List<MixEntry>, title: String) {
        if (released) return
        val desired = mix.mapTo(mutableSetOf()) { it.soundId }
        entries.keys.toList()
            .filterNot { it in desired }
            .forEach { entries.remove(it)?.track?.release() }
        mix.forEach { entry ->
            // Already-active sounds are left running by setSoundActive; setSoundLevel
            // then re-asserts the level for new and existing tracks alike.
            setSoundActive(entry.soundId, entry.audioRes, active = true)
            setSoundLevel(entry.soundId, entry.level)
        }
        setMixTitle(title)
        invalidateState()
    }

    fun setSoundActive(soundId: String, @RawRes audioRes: Int, active: Boolean) {
        if (released) return
        if (active) {
            if (entries.containsKey(soundId)) return
            val track = createTrack(soundId)
            track.start(audioRes)
            entries[soundId] = Entry(track, level = 1f)
            track.setVolume(masterVolume)
            if (!playWhenReadyValue) track.pause()
        } else {
            entries.remove(soundId)?.track?.release()
        }
        invalidateState()
    }

    fun setSoundLevel(soundId: String, level: Float) {
        if (released) return
        val entry = entries[soundId] ?: return
        entry.level = level.coerceIn(0f, 1f)
        entry.track.setVolume(entry.level * masterVolume)
        invalidateState()
    }

    fun setMixTitle(title: String) {
        if (released) return
        this.title = title
        invalidateState()
    }

    // --- SimpleBasePlayer ---

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReadyValue, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (entries.isEmpty()) Player.STATE_IDLE else Player.STATE_READY)
            .setVolume(masterVolume)

        if (entries.isNotEmpty()) {
            builder.setPlaylist(listOf(mixItem()))
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(0L)
        }
        return builder.build()
    }

    private fun mixItem(): MediaItemData =
        MediaItemData.Builder(MIX_ITEM_ID)
            .setMediaItem(MediaItem.Builder().setMediaId(MIX_ITEM_ID).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setIsSeekable(false)
            // Endless looping ambience: live, with no meaningful duration to report.
            .setIsDynamic(true)
            .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playWhenReadyValue = playWhenReady
        entries.values.forEach { if (playWhenReady) it.track.resume() else it.track.pause() }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    // The single-argument overload is deprecated in favor of handleSetVolume(Float, Int),
    // but it is still the one Player.setVolume(Float) routes to (Media3 1.10.1).
    @Suppress("OVERRIDE_DEPRECATION")
    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        masterVolume = volume.coerceIn(0f, 1f)
        entries.values.forEach { it.track.setVolume(it.level * masterVolume) }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        releaseAllTracks()
        playWhenReadyValue = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        releaseAllTracks()
        return Futures.immediateVoidFuture()
    }

    private fun releaseAllTracks() {
        entries.values.forEach { it.track.release() }
        entries.clear()
    }

    private companion object {
        const val MIX_ITEM_ID = "ambio_mix"
    }
}
