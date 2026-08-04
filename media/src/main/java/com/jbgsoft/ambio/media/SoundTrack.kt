package com.jbgsoft.ambio.media

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * One looping ambient sound. Narrow on purpose: MixPlayer holds five of these and
 * needs exactly these five operations, so the mixing logic can be unit-tested
 * against a fake instead of against the whole Player interface.
 */
interface SoundTrack {
    fun start(@RawRes audioRes: Int)
    fun setVolume(level: Float)
    fun pause()
    fun resume()
    fun release()
}

/** The real thing: one ExoPlayer looping one raw resource. */
class ExoPlayerSoundTrack(private val context: Context) : SoundTrack {

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            // false, deliberately: MixPlayer owns focus for the whole mix. With true, each
            // of the five players requested focus for itself and evicted the others — the
            // system keeps one entry per client, so only the newest sound stayed audible.
            false
        )
        // Also false: five players each pausing on an unplugged headset is five
        // uncoordinated decisions about one mix. MixPlayer's focus loss covers it.
        .setHandleAudioBecomingNoisy(false)
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ONE }

    override fun start(@RawRes audioRes: Int) {
        val uri = Uri.parse("android.resource://${context.packageName}/$audioRes")
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun setVolume(level: Float) { player.volume = level.coerceIn(0f, 1f) }
    override fun pause() { player.pause() }
    override fun resume() { player.play() }
    override fun release() { player.release() }
}
