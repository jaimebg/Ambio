package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.MixSlot
import com.jbgsoft.ambio.core.domain.model.Sound
import kotlinx.coroutines.flow.Flow

interface SoundRepository {
    fun getAllSounds(): List<Sound>
    fun getSoundById(id: String): Sound?

    /**
     * Never emits an empty list: at least one sound is always active.
     * The BREAK slot reads the FOCUS mix until it has been edited once.
     */
    fun getActiveMix(slot: MixSlot = MixSlot.FOCUS): Flow<List<ActiveSound>>

    /** Deactivating the last active sound is a no-op, enforced here and not only in the UI. */
    suspend fun setSoundActive(soundId: String, active: Boolean, slot: MixSlot = MixSlot.FOCUS)

    suspend fun setSoundLevel(soundId: String, level: Float, slot: MixSlot = MixSlot.FOCUS)
}
