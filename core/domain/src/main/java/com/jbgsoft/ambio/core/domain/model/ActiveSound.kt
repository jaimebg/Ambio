package com.jbgsoft.ambio.core.domain.model

/**
 * A sound that is currently part of the mix, with its own level.
 * The master volume is applied separately, in the media layer.
 */
data class ActiveSound(
    val sound: Sound,
    val level: Float
)
