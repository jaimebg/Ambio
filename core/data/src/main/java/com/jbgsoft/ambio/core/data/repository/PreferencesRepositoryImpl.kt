package com.jbgsoft.ambio.core.data.repository

import com.jbgsoft.ambio.core.data.datastore.PreferencesDataStore
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PreferencesRepositoryImpl @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = preferencesDataStore.preferences

    override suspend fun setLastSoundId(soundId: String) {
        preferencesDataStore.setLastSoundId(soundId)
    }

    override suspend fun setVolume(volume: Float) {
        preferencesDataStore.setVolume(volume)
    }

    override suspend fun setLastTimerMinutes(minutes: Int) {
        preferencesDataStore.setLastTimerMinutes(minutes)
    }

    override suspend fun setLastMode(mode: AppMode) {
        preferencesDataStore.setLastMode(mode)
    }
}
