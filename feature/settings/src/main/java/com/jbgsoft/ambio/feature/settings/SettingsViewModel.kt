package com.jbgsoft.ambio.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.value = SettingsUiState(
                    hapticsEnabled = prefs.hapticsEnabled,
                    chimeEnabled = prefs.chimeEnabled,
                    effectsEnabled = prefs.effectsEnabled
                )
            }
        }
    }

    fun onHapticsChanged(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun onChimeChanged(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setChimeEnabled(enabled) }
    }

    fun onEffectsChanged(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setEffectsEnabled(enabled) }
    }
}
