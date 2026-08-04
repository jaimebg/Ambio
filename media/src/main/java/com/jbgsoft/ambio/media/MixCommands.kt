package com.jbgsoft.ambio.media

/**
 * The control channel between AudioServiceConnection and the service.
 *
 * The connection talks to AudioService through a MediaController, and the Player
 * interface it exposes has no room for "set sound X to 60%". Media3's custom
 * session commands are that channel.
 *
 * Everything here is a primitive: the media module does not depend on core:domain
 * and does not start to. The service never learns what a Sound is.
 */
object MixCommands {
    const val SET_ACTIVE = "com.jbgsoft.ambio.SET_SOUND_ACTIVE"
    const val SET_LEVEL = "com.jbgsoft.ambio.SET_SOUND_LEVEL"
    const val SET_TITLE = "com.jbgsoft.ambio.SET_MIX_TITLE"

    const val ARG_SOUND_ID = "sound_id"
    const val ARG_AUDIO_RES = "audio_res"
    const val ARG_ACTIVE = "active"
    const val ARG_LEVEL = "level"
    const val ARG_TITLE = "title"
}
