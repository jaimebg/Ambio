package com.jbgsoft.ambio.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.common.audio.ChimePlayer
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import com.jbgsoft.ambio.core.common.resources.StringProvider
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.MixCodec
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
    private val chimeRepository: ChimeRepository,
    private val stringProvider: StringProvider
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
                if (isConnected) {
                    // The service keeps no per-sound state across a disconnect, so the
                    // whole mix has to be replayed every time the controller comes back.
                    _uiState.value.activeMix.forEach { active ->
                        audioServiceConnection.setSoundActive(
                            active.sound.id,
                            active.sound.audioRes,
                            true
                        )
                        audioServiceConnection.setSoundLevel(active.sound.id, active.level)
                    }
                    audioServiceConnection.setMixTitle(mixTitle(_uiState.value.activeMix))
                }
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

        soundRepository.getActiveMix()
            .onEach { mix ->
                _uiState.update { it.copy(activeMix = mix) }
                audioServiceConnection.setMixTitle(mixTitle(mix))
            }
            .launchIn(viewModelScope)
    }

    private fun observeTimerState() {
        timerRepository.timerState
            .onEach { state ->
                _uiState.update { it.copy(timerState = state) }
                if (state is TimerState.Completed) {
                    onTimerCompleted(wasBreak = state.wasBreak)
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
                            ?: state.customMinutes,
                        breakMinutes = prefs.breakMinutes,
                        hapticsEnabled = prefs.hapticsEnabled,
                        chimeEnabled = prefs.chimeEnabled,
                        effectsEnabled = prefs.effectsEnabled
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SetMode -> setMode(event.mode)
            is HomeEvent.ToggleSound -> toggleSound(event.sound)
            is HomeEvent.SetSoundLevel -> setSoundLevel(event.soundId, event.level)
            is HomeEvent.SelectPreset -> selectPreset(event.preset)
            is HomeEvent.SetCustomMinutes -> setCustomMinutes(event.minutes)
            is HomeEvent.CustomMinutesChangeFinished -> persistCustomMinutes()
            is HomeEvent.SetBreakMinutes -> setBreakMinutes(event.minutes)
            is HomeEvent.BreakMinutesChangeFinished -> persistBreakMinutes()
            is HomeEvent.SetVolume -> setVolume(event.volume, persist = false)
            is HomeEvent.VolumeChangeFinished -> persistVolume()
            is HomeEvent.PlayPause -> playPause()
            is HomeEvent.Reset -> reset()
            is HomeEvent.ShowSoundPicker -> showSoundPicker()
            is HomeEvent.HideSoundPicker -> hideSoundPicker()
            is HomeEvent.TimerCompleted -> onTimerCompleted(wasBreak = false)
        }
    }

    private fun setMode(mode: AppMode) {
        haptic { click() }

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

    private fun toggleSound(sound: Sound) {
        haptic { heavyClick() }
        val isActive = _uiState.value.activeMix.any { it.sound.id == sound.id }
        // The repository refuses to empty the mix; don't tell the service otherwise.
        if (isActive && _uiState.value.activeMix.size == 1) return
        viewModelScope.launch {
            soundRepository.setSoundActive(sound.id, active = !isActive)
        }
        audioServiceConnection.setSoundActive(sound.id, sound.audioRes, !isActive)
    }

    private fun setSoundLevel(soundId: String, level: Float) {
        viewModelScope.launch { soundRepository.setSoundLevel(soundId, level) }
        audioServiceConnection.setSoundLevel(soundId, level)
    }

    private fun mixTitle(mix: List<ActiveSound>): String =
        if (mix.size <= 2) {
            mix.joinToString(" + ") { stringProvider.get(it.sound.nameRes) }
        } else {
            stringProvider.get(R.string.mix_sound_count, mix.size)
        }

    /**
     * The mix itself already reached the service through [observeAudioServiceState];
     * starting playback is only the master volume plus the fade-in.
     */
    private fun startPlayback() {
        audioServiceConnection.setVolume(_uiState.value.volume)
        audioServiceConnection.play()
    }

    private fun selectPreset(preset: TimerPreset) {
        haptic { click() }
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
        haptic { tick() }
        viewModelScope.launch {
            preferencesRepository.setLastTimerMinutes(_uiState.value.customMinutes)
        }
    }

    private fun setBreakMinutes(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(1, 30)
        _uiState.update { it.copy(breakMinutes = clampedMinutes) }
    }

    private fun persistBreakMinutes() {
        haptic { tick() }
        viewModelScope.launch {
            preferencesRepository.setBreakMinutes(_uiState.value.breakMinutes)
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
        haptic { heavyClick() }
        val state = _uiState.value

        viewModelScope.launch {
            when {
                state.mode == AppMode.AMBIENT -> {
                    // In ambient mode, just toggle play/pause for audio
                    if (state.isPlaying) {
                        audioServiceConnection.pause()
                    } else {
                        startPlayback()
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
                    startPlayback()
                }
            }
        }
    }

    private fun reset() {
        haptic { click() }
        viewModelScope.launch {
            timerRepository.resetTimer()
            audioServiceConnection.stop()
        }
    }

    private fun showSoundPicker() {
        haptic { click() }
        _uiState.update { it.copy(showSoundPicker = true) }
    }

    private fun hideSoundPicker() {
        _uiState.update { it.copy(showSoundPicker = false) }
    }

    private fun onTimerCompleted(wasBreak: Boolean) {
        val state = _uiState.value

        // Stop the ambient sound when timer completes
        audioServiceConnection.stop()

        // Play timer completion chime and haptic feedback
        if (_uiState.value.chimeEnabled) {
            chimePlayer.playChime(chimeRepository.getTimerChimeResource())
        }
        haptic { timerComplete() }

        if (wasBreak) {
            // Break finished - reset to idle state, don't start another break
            viewModelScope.launch {
                timerRepository.resetTimer()
            }
            return
        }

        // Focus session completed
        viewModelScope.launch {
            // Save the completed session against the whole mix, not one sound
            val mix = state.activeMix
            if (mix.isNotEmpty()) {
                val minutes = when (state.selectedPreset) {
                    TimerPreset.FOCUS_25 -> 25
                    TimerPreset.FOCUS_50 -> 50
                    TimerPreset.CUSTOM -> state.customMinutes
                }
                saveSessionUseCase(
                    soundId = MixCodec.encode(mix, withLevels = false),
                    durationMinutes = minutes,
                    wasCompleted = true
                )
            }

            // Start break timer
            timerRepository.startBreak(state.breakMinutes * 60 * 1000L)
        }
    }

    private fun haptic(action: HapticManager.() -> Unit) {
        if (_uiState.value.hapticsEnabled) hapticManager.action()
    }
}
