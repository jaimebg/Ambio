package com.jbgsoft.ambio.media

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Both halves of [MixCommands.SET_MIX]'s wire format, kept together so the writer and
 * the reader cannot drift apart.
 *
 * [decode] answers null — never an empty list — for anything it does not fully
 * understand. The distinction is the whole point: an empty mix is a valid instruction
 * meaning "release every track", and the repository never produces one, so a bundle
 * that decodes to nothing is a bug, not a request for silence. Refusing it leaves the
 * mix exactly as it is, which is the only safe response to a malformed message.
 */
object MixBundle {

    fun encode(mix: List<MixEntry>, title: String): Bundle = Bundle().apply {
        putStringArray(MixCommands.ARG_SOUND_IDS, mix.map { it.soundId }.toTypedArray())
        putIntArray(MixCommands.ARG_AUDIO_RES, mix.map { it.audioRes }.toIntArray())
        putFloatArray(
            MixCommands.ARG_LEVELS,
            mix.map { it.level.coerceIn(0f, 1f) }.toFloatArray()
        )
        putString(MixCommands.ARG_TITLE, title)
    }

    /**
     * Null if any of the three arrays is missing, if a null id shortens one of them,
     * if their lengths disagree, or if the mix is empty.
     */
    fun decode(args: Bundle): List<MixEntry>? {
        val ids = args.getStringArray(MixCommands.ARG_SOUND_IDS)?.filterNotNull() ?: return null
        val audioRes = args.getIntArray(MixCommands.ARG_AUDIO_RES) ?: return null
        val levels = args.getFloatArray(MixCommands.ARG_LEVELS) ?: return null
        if (ids.isEmpty()) return null
        if (ids.size != audioRes.size || ids.size != levels.size) return null
        return ids.indices.map { MixEntry(ids[it], audioRes[it], levels[it]) }
    }

    fun decodeTitle(args: Bundle): String = args.getString(MixCommands.ARG_TITLE).orEmpty()
}

/**
 * Applies a SET_MIX bundle to [player], or refuses it outright.
 *
 * Returns false and touches nothing when the bundle cannot be decoded. Extracted from
 * the session callback so the refusal can be tested against a real [MixPlayer]: the
 * failure this guards against is not "the bundle is odd", it is "the bundle is odd and
 * every track goes silent".
 */
@OptIn(markerClass = [UnstableApi::class])
internal fun applySetMix(player: MixPlayer, args: Bundle): Boolean {
    val mix = MixBundle.decode(args) ?: return false
    player.setMix(mix, MixBundle.decodeTitle(args))
    return true
}
