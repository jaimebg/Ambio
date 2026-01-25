package com.jbgsoft.ambio.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ambio_preferences")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val LAST_SOUND_ID = stringPreferencesKey("last_sound_id")
        val VOLUME = floatPreferencesKey("volume")
        val LAST_TIMER_MINUTES = intPreferencesKey("last_timer_minutes")
        val LAST_MODE = stringPreferencesKey("last_mode")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            lastSoundId = prefs[PreferencesKeys.LAST_SOUND_ID] ?: "rain",
            volume = prefs[PreferencesKeys.VOLUME] ?: 0.7f,
            lastTimerMinutes = prefs[PreferencesKeys.LAST_TIMER_MINUTES] ?: 25,
            lastMode = prefs[PreferencesKeys.LAST_MODE]?.let {
                AppMode.valueOf(it)
            } ?: AppMode.TIMER
        )
    }

    suspend fun setLastSoundId(soundId: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SOUND_ID] = soundId
        }
    }

    suspend fun setVolume(volume: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.VOLUME] = volume
        }
    }

    suspend fun setLastTimerMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_TIMER_MINUTES] = minutes
        }
    }

    suspend fun setLastMode(mode: AppMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_MODE] = mode.name
        }
    }
}
