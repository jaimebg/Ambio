package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound
import kotlinx.coroutines.flow.Flow

interface SoundRepository {
    fun getAllSounds(): List<Sound>
    fun getSoundById(id: String): Sound?

    /** Never emits an empty list: at least one sound is always active. */
    fun getActiveMix(): Flow<List<ActiveSound>>

    /** Deactivating the last active sound is a no-op, enforced here and not only in the UI. */
    suspend fun setSoundActive(soundId: String, active: Boolean)

    suspend fun setSoundLevel(soundId: String, level: Float)
}
