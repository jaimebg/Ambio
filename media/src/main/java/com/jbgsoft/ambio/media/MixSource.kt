package com.jbgsoft.ambio.media

/**
 * Where the service gets the mix to play when it starts with none.
 *
 * Declared here, over media's own [MixEntry], so this module keeps declaring no project
 * dependency at all — the stored mix lives behind SoundRepository in core:domain, which
 * media is not allowed to reach. Whoever can see both implements this; today that is
 * core:di.
 */
interface MixSource {
    /** Never empty in practice: the repository guarantees at least one active sound. */
    suspend fun currentMix(): List<MixEntry>
}
