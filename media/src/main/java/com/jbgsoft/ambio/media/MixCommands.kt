package com.jbgsoft.ambio.media

/**
 * The control channel between AudioServiceConnection and the service.
 *
 * The connection talks to AudioService through a MediaController, and the Player
 * interface it exposes has no room for "set sound X to 60%". Media3's custom
 * session commands are that channel.
 *
 * There is exactly one command, and it carries the *whole* mix rather than a delta.
 * That is deliberate: the service's track set is cleared by stop() and by its own
 * death, and a delta channel has no way to rebuild it. A full-state message is
 * idempotent, so re-sending it is always safe and always sufficient — which is what
 * lets the mix be re-asserted on every emission, on every reconnect, and before
 * every play() without anyone keeping a shadow copy of the service's state.
 *
 * Everything here is a primitive: the media module does not depend on core:domain
 * and does not start to. The service never learns what a Sound is.
 */
object MixCommands {
    const val SET_MIX = "com.jbgsoft.ambio.SET_MIX"

    /** Parallel arrays, all of the same length: index i describes one sound. */
    const val ARG_SOUND_IDS = "sound_ids"
    const val ARG_AUDIO_RES = "audio_res"
    const val ARG_LEVELS = "levels"

    const val ARG_TITLE = "title"
}
