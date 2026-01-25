package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.Sound
import kotlinx.coroutines.flow.Flow

interface SoundRepository {
    fun getAllSounds(): List<Sound>
    fun getSoundById(id: String): Sound?
    fun getSelectedSound(): Flow<Sound>
    suspend fun setSelectedSound(soundId: String)
}
