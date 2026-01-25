package com.jbgsoft.ambio.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import com.jbgsoft.ambio.core.domain.usecase.SaveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val soundRepository: SoundRepository,
    private val timerRepository: TimerRepository,
    private val preferencesRepository: PreferencesRepository,
    private val saveSessionUseCase: SaveSessionUseCase,
    private val hapticManager: HapticManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observeTimerState()
        observePreferences()
    }

    private fun loadInitialData() {
        val sounds = soundRepository.getAllSounds()
        _uiState.update { it.copy(availableSounds = sounds) }

        soundRepository.getSelectedSound()
            .onEach { sound ->
                _uiState.update { it.copy(selectedSound = sound) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeTimerState() {
        timerRepository.timerState
            .onEach { state ->
                _uiState.update { it.copy(timerState = state) }
                if (state is TimerState.Completed) {
                    onTimerCompleted()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observePreferences() {
        preferencesRepository.preferences
            .onEach { prefs ->
                _uiState.update { state ->
                    state.copy(
                        volume = prefs.volume,
                        mode = prefs.lastMode,
                        customMinutes = prefs.lastTimerMinutes.takeIf { it !in listOf(25, 50) }
                            ?: state.customMinutes
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SetMode -> setMode(event.mode)
            is HomeEvent.SelectSound -> selectSound(event.sound)
            is HomeEvent.SelectPreset -> selectPreset(event.preset)
            is HomeEvent.SetCustomMinutes -> setCustomMinutes(event.minutes)
            is HomeEvent.SetVolume -> setVolume(event.volume)
            is HomeEvent.PlayPause -> playPause()
            is HomeEvent.Reset -> reset()
            is HomeEvent.ShowSoundPicker -> showSoundPicker()
            is HomeEvent.HideSoundPicker -> hideSoundPicker()
            is HomeEvent.TimerCompleted -> onTimerCompleted()
        }
    }

    private fun setMode(mode: AppMode) {
        hapticManager.click()
        _uiState.update { it.copy(mode = mode) }
        viewModelScope.launch {
            preferencesRepository.setLastMode(mode)
        }
    }

    private fun selectSound(sound: Sound) {
        hapticManager.heavyClick()
        viewModelScope.launch {
            soundRepository.setSelectedSound(sound.id)
            preferencesRepository.setLastSoundId(sound.id)
        }
        _uiState.update { it.copy(showSoundPicker = false) }
    }

    private fun selectPreset(preset: TimerPreset) {
        hapticManager.click()
        _uiState.update { it.copy(selectedPreset = preset) }
        if (preset != TimerPreset.CUSTOM) {
            viewModelScope.launch {
                preferencesRepository.setLastTimerMinutes(preset.focusMinutes)
            }
        }
    }

    private fun setCustomMinutes(minutes: Int) {
        hapticManager.tick()
        val clampedMinutes = minutes.coerceIn(1, 120)
        _uiState.update { it.copy(customMinutes = clampedMinutes) }
        viewModelScope.launch {
            preferencesRepository.setLastTimerMinutes(clampedMinutes)
        }
    }

    private fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clampedVolume) }
        viewModelScope.launch {
            preferencesRepository.setVolume(clampedVolume)
        }
    }

    private fun playPause() {
        hapticManager.heavyClick()
        val state = _uiState.value

        viewModelScope.launch {
            when {
                state.mode == AppMode.AMBIENT -> {
                    // In ambient mode, just toggle play/pause
                    _uiState.update { it.copy(isPlaying = !it.isPlaying) }
                }
                state.timerState is TimerState.Running -> {
                    timerRepository.pauseTimer()
                    _uiState.update { it.copy(isPlaying = false) }
                }
                state.timerState is TimerState.Paused -> {
                    timerRepository.resumeTimer()
                    _uiState.update { it.copy(isPlaying = true) }
                }
                else -> {
                    // Start timer
                    val minutes = when (state.selectedPreset) {
                        TimerPreset.FOCUS_25 -> 25
                        TimerPreset.FOCUS_50 -> 50
                        TimerPreset.CUSTOM -> state.customMinutes
                    }
                    val durationMs = minutes * 60 * 1000L
                    timerRepository.startTimer(durationMs)
                    _uiState.update { it.copy(isPlaying = true) }
                }
            }
        }
    }

    private fun reset() {
        hapticManager.click()
        viewModelScope.launch {
            timerRepository.resetTimer()
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private fun showSoundPicker() {
        hapticManager.click()
        _uiState.update { it.copy(showSoundPicker = true) }
    }

    private fun hideSoundPicker() {
        _uiState.update { it.copy(showSoundPicker = false) }
    }

    private fun onTimerCompleted() {
        val state = _uiState.value
        hapticManager.timerComplete()

        viewModelScope.launch {
            // Save the completed session
            state.selectedSound?.let { sound ->
                val minutes = when (state.selectedPreset) {
                    TimerPreset.FOCUS_25 -> 25
                    TimerPreset.FOCUS_50 -> 50
                    TimerPreset.CUSTOM -> state.customMinutes
                }
                saveSessionUseCase(
                    soundId = sound.id,
                    durationMinutes = minutes,
                    wasCompleted = true
                )
            }

            // Start break if applicable
            val breakMinutes = when (state.selectedPreset) {
                TimerPreset.FOCUS_25 -> 5
                TimerPreset.FOCUS_50 -> 10
                TimerPreset.CUSTOM -> 5 // Default 5 min break for custom
            }
            timerRepository.startBreak(breakMinutes * 60 * 1000L)
        }
    }

    fun updateServiceConnection(isConnected: Boolean) {
        _uiState.update { it.copy(isServiceConnected = isConnected) }
    }

    fun updateIsPlaying(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
    }
}
