package com.jbgsoft.ambio.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.common.audio.ChimePlayer
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.repository.ChimeRepository
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import com.jbgsoft.ambio.core.domain.usecase.SaveSessionUseCase
import com.jbgsoft.ambio.media.AudioServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val hapticManager: HapticManager,
    private val audioServiceConnection: AudioServiceConnection,
    private val chimePlayer: ChimePlayer,
    private val chimeRepository: ChimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        connectAudioService()
        loadInitialData()
        observeTimerState()
        observePreferences()
        observeAudioServiceState()
    }

    private fun connectAudioService() {
        audioServiceConnection.connect()
    }

    private fun observeAudioServiceState() {
        audioServiceConnection.isConnected
            .onEach { isConnected ->
                _uiState.update { it.copy(isServiceConnected = isConnected) }
            }
            .launchIn(viewModelScope)

        audioServiceConnection.isPlaying
            .onEach { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        audioServiceConnection.disconnect()
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
            is HomeEvent.CustomMinutesChangeFinished -> persistCustomMinutes()
            is HomeEvent.SetVolume -> setVolume(event.volume, persist = false)
            is HomeEvent.VolumeChangeFinished -> persistVolume()
            is HomeEvent.PlayPause -> playPause()
            is HomeEvent.Reset -> reset()
            is HomeEvent.ShowSoundPicker -> showSoundPicker()
            is HomeEvent.HideSoundPicker -> hideSoundPicker()
            is HomeEvent.TimerCompleted -> onTimerCompleted()
        }
    }

    private fun setMode(mode: AppMode) {
        hapticManager.click()

        // Reset timer when switching to Ambient mode if timer is active
        if (mode == AppMode.AMBIENT) {
            val timerState = _uiState.value.timerState
            if (timerState is TimerState.Running || timerState is TimerState.Paused) {
                viewModelScope.launch {
                    timerRepository.resetTimer()
                }
            }
        }

        // Stop audio when switching to Timer mode so button shows "play"
        if (mode == AppMode.TIMER && _uiState.value.isPlaying) {
            audioServiceConnection.stop()
        }

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

        // If currently playing, switch to the new sound
        if (_uiState.value.isPlaying) {
            playSoundAudio(sound)
        }
    }

    private fun playSoundAudio(sound: Sound) {
        val description = when (_uiState.value.mode) {
            AppMode.TIMER -> {
                val timerState = _uiState.value.timerState
                if (timerState is TimerState.Running) {
                    val minutes = timerState.remainingMs / 60000
                    val seconds = (timerState.remainingMs % 60000) / 1000
                    "${minutes}:${seconds.toString().padStart(2, '0')} remaining"
                } else {
                    "Focus Timer"
                }
            }
            AppMode.AMBIENT -> "Ambient Mode"
        }
        audioServiceConnection.playSound(
            audioRes = sound.audioRes,
            name = sound.name,
            description = description,
            illustrationRes = sound.illustrationRes
        )
        // Apply current volume
        audioServiceConnection.setVolume(_uiState.value.volume)
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
        val clampedMinutes = minutes.coerceIn(1, 120)
        _uiState.update { it.copy(customMinutes = clampedMinutes) }
    }

    private fun persistCustomMinutes() {
        hapticManager.tick()
        viewModelScope.launch {
            preferencesRepository.setLastTimerMinutes(_uiState.value.customMinutes)
        }
    }

    private fun setVolume(volume: Float, persist: Boolean = true) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clampedVolume) }
        // Apply volume to audio service immediately for real-time feedback
        audioServiceConnection.setVolume(clampedVolume)
        // Only persist to DataStore when dragging finishes to avoid lag
        if (persist) {
            viewModelScope.launch {
                preferencesRepository.setVolume(clampedVolume)
            }
        }
    }

    private fun persistVolume() {
        viewModelScope.launch {
            preferencesRepository.setVolume(_uiState.value.volume)
        }
    }

    private fun playPause() {
        hapticManager.heavyClick()
        val state = _uiState.value

        viewModelScope.launch {
            when {
                state.mode == AppMode.AMBIENT -> {
                    // In ambient mode, just toggle play/pause for audio
                    if (state.isPlaying) {
                        audioServiceConnection.pause()
                    } else {
                        state.selectedSound?.let { sound ->
                            playSoundAudio(sound)
                        }
                    }
                }
                state.timerState is TimerState.Running -> {
                    // Pause both timer and audio
                    timerRepository.pauseTimer()
                    audioServiceConnection.pause()
                }
                state.timerState is TimerState.Paused -> {
                    // Resume both timer and audio
                    timerRepository.resumeTimer()
                    audioServiceConnection.play()
                }
                else -> {
                    // Start timer and play sound
                    val minutes = when (state.selectedPreset) {
                        TimerPreset.FOCUS_25 -> 25
                        TimerPreset.FOCUS_50 -> 50
                        TimerPreset.CUSTOM -> state.customMinutes
                    }
                    val durationMs = minutes * 60 * 1000L
                    timerRepository.startTimer(durationMs)
                    state.selectedSound?.let { sound ->
                        playSoundAudio(sound)
                    }
                }
            }
        }
    }

    private fun reset() {
        hapticManager.click()
        viewModelScope.launch {
            timerRepository.resetTimer()
            audioServiceConnection.stop()
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

        // Stop the ambient sound when timer completes
        audioServiceConnection.stop()

        // Play timer completion chime and haptic feedback
        chimePlayer.playChime(chimeRepository.getTimerChimeResource())
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
}
