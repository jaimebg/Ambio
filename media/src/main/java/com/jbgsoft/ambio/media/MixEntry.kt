package com.jbgsoft.ambio.media

import androidx.annotation.RawRes

/**
 * One sound in a mix, as the media module sees it: an opaque id, a raw resource and
 * a level. Deliberately not a domain type — media does not depend on core:domain, so
 * the caller flattens its own model into these before crossing the session boundary.
 */
data class MixEntry(
    val soundId: String,
    @param:RawRes val audioRes: Int,
    val level: Float
)
