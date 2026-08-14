package com.jbgsoft.ambio.media

import android.os.Handler
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
    private val looper: Looper,
    private val createTrack: (soundId: String) -> SoundTrack,
    private val audioFocus: AudioFocus,
    private val onPlayRequestedWithEmptyMix: () -> Unit
) : SimpleBasePlayer(looper) {

    // ramp is the fade multiplier, 0..1. Membership decides its direction: an entry in
    // `entries` is heading for 1, so no per-entry target field is needed.
    private class Entry(val track: SoundTrack, var level: Float) {
        var ramp: Float = 0f
    }

    private val entries = LinkedHashMap<String, Entry>()

    // Sounds fading toward silence. Deliberately NOT part of getState(): the fade is
    // audio cleanup, and a mix the user has emptied must report itself empty at once.
    private val retiring = LinkedHashMap<String, Entry>()
    private var playWhenReadyValue = false
    private var masterVolume = 1f
    private var title = ""

    // Ducking must not overwrite masterVolume, or there is nothing exact to restore.
    private var duckMultiplier = 1f

    private val ticker = Handler(looper)
    private var ticking = false

    // A pause the system caused is not a pause the user asked for. Only the first resumes
    // when focus comes back; conflating them makes hanging up a call restart audio the
    // user had deliberately silenced.
    private var pausedByFocusLoss = false

    init {
        audioFocus.onChange(::onFocusChange)
    }

    private fun onFocusChange(change: FocusChange) {
        when (change) {
            FocusChange.LOST -> {
                if (playWhenReadyValue) pauseForFocusLoss()
                audioFocus.abandon()
            }
            FocusChange.LOST_TRANSIENT -> if (playWhenReadyValue) pauseForFocusLoss()
            FocusChange.LOST_TRANSIENT_DUCK -> {
                duckMultiplier = DUCK_MULTIPLIER
                applyVolumes()
            }
            FocusChange.GAINED -> {
                duckMultiplier = 1f
                applyVolumes()
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    playWhenReadyValue = true
                    entries.values.forEach { it.track.resume() }
                }
            }
        }
        invalidateState()
    }

    private fun pauseForFocusLoss() {
        pausedByFocusLoss = true
        playWhenReadyValue = false
        settleRamps()
        entries.values.forEach { it.track.pause() }
    }

    private fun applyVolumes() {
        entries.values.forEach { it.applyVolume() }
        retiring.values.forEach { it.applyVolume() }
    }

    private fun Entry.applyVolume() {
        track.setVolume(level * ramp * masterVolume * duckMultiplier)
    }

    /**
     * Advances every live ramp and stops rescheduling itself once they have all
     * settled, so an idle mix costs nothing.
     */
    private val tick = object : Runnable {
        override fun run() {
            var stillMoving = false
            entries.values.forEach {
                if (it.ramp < 1f) {
                    it.ramp = stepToward(it.ramp, 1f, FADE_IN_MS)
                    if (it.ramp < 1f) stillMoving = true
                }
            }
            val leaving = retiring.entries.iterator()
            while (leaving.hasNext()) {
                val entry = leaving.next().value
                entry.ramp = stepToward(entry.ramp, 0f, FADE_OUT_MS)
                if (entry.ramp <= 0f) {
                    entry.track.release()
                    leaving.remove()
                } else {
                    stillMoving = true
                }
            }
            applyVolumes()
            ticking = stillMoving
            if (stillMoving) ticker.postDelayed(this, TICK_MS)
        }
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        ticker.postDelayed(tick, TICK_MS)
    }

    /**
     * Jumps every ramp to where it was heading and stops the ticker. Used whenever the
     * mix is not audible: a ramp nobody can hear is just a delayed volume change.
     */
    private fun settleRamps() {
        entries.values.forEach { it.ramp = 1f }
        retiring.values.forEach { it.track.release() }
        retiring.clear()
        ticker.removeCallbacks(tick)
        ticking = false
        applyVolumes()
    }

    // The step is derived from a full 0->1 duration, so an interrupted ramp keeps the
    // same slew rate and simply has less distance left to cover.
    private fun stepToward(current: Float, target: Float, durationMs: Long): Float {
        val delta = TICK_MS.toFloat() / durationMs
        return if (target > current) (current + delta).coerceAtMost(target)
        else (current - delta).coerceAtLeast(target)
    }

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
            .forEach { deactivate(it) }
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
            // A sound still fading out comes back from where it is, rather than
            // starting a second decoder for something already playing.
            val revived = retiring.remove(soundId)
            entries[soundId] = revived ?: Entry(
                createTrack(soundId).also { it.start(audioRes) },
                level = 1f
            )
            if (playWhenReadyValue) {
                applyVolumes()
                startTicking()
            } else {
                entries.getValue(soundId).track.pause()
                settleRamps()
            }
        } else {
            deactivate(soundId)
        }
        invalidateState()
    }

    private fun deactivate(soundId: String) {
        val entry = entries.remove(soundId) ?: return
        if (playWhenReadyValue) {
            retiring[soundId] = entry
            startTicking()
        } else {
            // Nothing to fade toward silence when nothing is audible; freeing the
            // decoder now beats holding it for 600ms of inaudible ramp.
            entry.track.release()
            // Settle rather than just release: once Task 3 lands, losing a sound
            // changes the bus for every remaining track, and that has to reach them
            // even though no ticker is running while paused.
            settleRamps()
        }
    }

    fun setSoundLevel(soundId: String, level: Float) {
        if (released) return
        val entry = entries[soundId] ?: return
        entry.level = level.coerceIn(0f, 1f)
        entry.applyVolume()
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
        if (playWhenReady) {
            // Before anything else, and specifically before asking for audio focus.
            // A play request can arrive from the Quick Settings tile with the app closed,
            // and this player then holds nothing: taking focus at that point silences
            // whatever the user was actually listening to, in order to play silence.
            // The service owns the fix — it can read the stored mix, and this class
            // deliberately cannot.
            if (entries.isEmpty()) {
                onPlayRequestedWithEmptyMix()
                return Futures.immediateVoidFuture()
            }
            // Do not play without focus; a denied request leaves the mix paused.
            if (!audioFocus.request()) return Futures.immediateVoidFuture()
            pausedByFocusLoss = false
            // A granted request means we hold full focus, so nothing is ducking us any
            // more. GAINED cannot be relied on to clear this: a permanent LOST abandons
            // the focus, so no GAINED will ever arrive, and without this the mix would
            // come back at 0.2x for good.
            duckMultiplier = 1f
            // Set before the calls below, which branch on the mix being audible.
            playWhenReadyValue = true
            settleRamps()
        } else {
            // A deliberate pause keeps the focus: abandoning and re-requesting on every
            // pause would let another app take our place while the user is deciding.
            pausedByFocusLoss = false
            playWhenReadyValue = false
            settleRamps()
        }
        entries.values.forEach { if (playWhenReady) it.track.resume() else it.track.pause() }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    // The single-argument overload is deprecated in favor of handleSetVolume(Float, Int),
    // but it is still the one Player.setVolume(Float) routes to (Media3 1.10.1).
    @Suppress("OVERRIDE_DEPRECATION")
    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        masterVolume = volume.coerceIn(0f, 1f)
        applyVolumes()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        releaseAllTracks()
        playWhenReadyValue = false
        pausedByFocusLoss = false
        // The duck belonged to a focus we no longer hold. Left set, the next setMix()
        // would build its tracks at 0.2x (setSoundActive applies it at creation).
        duckMultiplier = 1f
        audioFocus.abandon()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        releaseAllTracks()
        audioFocus.abandon()
        return Futures.immediateVoidFuture()
    }

    private fun releaseAllTracks() {
        ticker.removeCallbacks(tick)
        ticking = false
        entries.values.forEach { it.track.release() }
        entries.clear()
        retiring.values.forEach { it.track.release() }
        retiring.clear()
    }

    internal companion object {
        const val MIX_ITEM_ID = "ambio_mix"
        const val DUCK_MULTIPLIER = 0.2f
        const val FADE_IN_MS = 1500L
        const val FADE_OUT_MS = 600L
        const val TICK_MS = 50L
    }
}
