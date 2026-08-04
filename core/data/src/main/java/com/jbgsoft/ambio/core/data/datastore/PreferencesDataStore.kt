package com.jbgsoft.ambio.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        // The key string must stay "last_sound_id": it now holds a mix, but changing
        // the string would compile fine and silently wipe every user's stored mix.
        val LAST_SOUND_ID = stringPreferencesKey("last_sound_id")
        val VOLUME = floatPreferencesKey("volume")
        val LAST_TIMER_MINUTES = intPreferencesKey("last_timer_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val LAST_MODE = stringPreferencesKey("last_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val CHIME_ENABLED = booleanPreferencesKey("chime_enabled")
        val EFFECTS_ENABLED = booleanPreferencesKey("effects_enabled")
    }

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            lastMix = prefs[PreferencesKeys.LAST_SOUND_ID] ?: "rain",
            volume = prefs[PreferencesKeys.VOLUME] ?: 0.7f,
            lastTimerMinutes = prefs[PreferencesKeys.LAST_TIMER_MINUTES] ?: 25,
            breakMinutes = prefs[PreferencesKeys.BREAK_MINUTES] ?: 5,
            lastMode = prefs[PreferencesKeys.LAST_MODE]?.let {
                AppMode.valueOf(it)
            } ?: AppMode.TIMER,
            hapticsEnabled = prefs[PreferencesKeys.HAPTICS_ENABLED] ?: true,
            chimeEnabled = prefs[PreferencesKeys.CHIME_ENABLED] ?: true,
            effectsEnabled = prefs[PreferencesKeys.EFFECTS_ENABLED] ?: true
        )
    }

    suspend fun setLastMix(encoded: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SOUND_ID] = encoded
        }
    }

    suspend fun setVolume(volume: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.VOLUME] = volume
        }
    }

    suspend fun setLastTimerMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_TIMER_MINUTES] = minutes
        }
    }

    suspend fun setLastMode(mode: AppMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_MODE] = mode.name
        }
    }

    suspend fun setBreakMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.BREAK_MINUTES] = minutes
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setChimeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CHIME_ENABLED] = enabled
        }
    }

    suspend fun setEffectsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.EFFECTS_ENABLED] = enabled
        }
    }
}
