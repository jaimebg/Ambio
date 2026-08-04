package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setLastMix(encoded: String)
    suspend fun setVolume(volume: Float)
    suspend fun setLastTimerMinutes(minutes: Int)
    suspend fun setBreakMinutes(minutes: Int)
    suspend fun setLastMode(mode: AppMode)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setChimeEnabled(enabled: Boolean)
    suspend fun setEffectsEnabled(enabled: Boolean)
}
