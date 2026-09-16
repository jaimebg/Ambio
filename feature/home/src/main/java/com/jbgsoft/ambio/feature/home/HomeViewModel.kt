package com.jbgsoft.ambio.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.common.audio.ChimePlayer
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import com.jbgsoft.ambio.core.common.resources.StringProvider
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.MixCodec
import com.jbgsoft.ambio.core.domain.model.MixSlot
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.model.withAddedStep
import com.jbgsoft.ambio.core.domain.model.withRowChoice
import com.jbgsoft.ambio.core.domain.model.withStepMinutes
import com.jbgsoft.ambio.core.domain.repository.ChimeRepository
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import com.jbgsoft.ambio.core.domain.session.SessionEvent
import com.jbgsoft.ambio.core.domain.session.SessionRunner
import com.jbgsoft.ambio.core.domain.usecase.SaveSessionUseCase
import com.jbgsoft.ambio.media.AudioServiceConnection
import com.jbgsoft.ambio.media.MixEntry
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
    private val sessionRunner: SessionRunner,
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
        observeSession()
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
                // The service keeps no state across a disconnect, so re-assert the mix
                // whenever the controller comes up. If the mix has not resolved yet this
                // is a no-op and the getActiveMix observer does the push instead — the
                // two orderings converge because both send full state.
                if (isConnected) pushMix()
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

        soundRepository.getActiveMix(MixSlot.FOCUS)
            .onEach { mix -> onMixEmitted(MixSlot.FOCUS, mix) }
            .launchIn(viewModelScope)

        soundRepository.getActiveMix(MixSlot.BREAK)
            .onEach { mix -> onMixEmitted(MixSlot.BREAK, mix) }
            .launchIn(viewModelScope)
    }

    /** Only the audible slot's emissions reach the service; the other one just lands in state. */
    private fun onMixEmitted(slot: MixSlot, mix: List<ActiveSound>) {
        _uiState.update { state ->
            if (slot == MixSlot.BREAK) state.copy(breakMix = mix) else state.copy(focusMix = mix)
        }
        if (_uiState.value.audibleSlot == slot) pushMix()
    }

    /**
     * The single place the service is told anything about the mix.
     *
     * The repository decides what the mix is; this pushes whatever it decided, in
     * full. Nothing sends the service a delta, so there is no path where the two can
     * disagree: any missed or reordered push is repaired by the next one. Called on
     * every emission, on every reconnect, and before every play() — stop() releases
     * the service's tracks, so playing again has to re-declare them.
     */
    private fun pushMix(mix: List<ActiveSound> = _uiState.value.activeMix) {
        if (mix.isEmpty()) return
        audioServiceConnection.setMix(
            mix.map { MixEntry(it.sound.id, it.sound.audioRes, it.level) },
            mixTitle(mix)
        )
    }

    private fun observeTimerState() {
        timerRepository.timerState
            .onEach { state -> _uiState.update { it.copy(timerState = state) } }
            .launchIn(viewModelScope)
    }

    private fun observeSession() {
        sessionRunner.progress
            .onEach { progress ->
                _uiState.update { it.copy(sessionProgress = progress) }
                // No plan in progress means FOCUS again. Left on BREAK, an idle or
                // ambient screen would wear the break palette and — worse — drop every
                // focus mix emission as "not the audible slot".
                if (progress == null) applyAudibility(null)
            }
            .launchIn(viewModelScope)

        sessionRunner.events
            .onEach { event -> onSessionEvent(event) }
            .launchIn(viewModelScope)
    }

    private fun observePreferences() {
        preferencesRepository.preferences
            .onEach { prefs ->
                val previous = _uiState.value
                _uiState.update { state ->
                    state.copy(
                        volume = prefs.volume,
                        mode = prefs.lastMode,
                        customMinutes = prefs.lastTimerMinutes.takeIf { it !in listOf(25, 50) }
                            ?: state.customMinutes,
                        breakMinutes = prefs.breakMinutes,
                        sessionPlan = prefs.sessionPlan,
                        hapticsEnabled = prefs.hapticsEnabled,
                        chimeEnabled = prefs.chimeEnabled,
                        effectsEnabled = prefs.effectsEnabled,
                        breakSoundEnabled = prefs.breakSoundEnabled
                    )
                }
                if (previous.breakSoundEnabled != prefs.breakSoundEnabled) onBreakSoundToggled()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Flipping the toggle mid-break must be audible at once — but only while the
     * session is actually running. Paused or idle there is nothing to make audible:
     * applying it would restart playback the user had paused. The slot still moves,
     * so the gradient and mix bar show what the next resume would play, and the
     * resume itself (which runs [applyAudibility] while the timer is still Paused)
     * is what turns it into sound.
     */
    private fun onBreakSoundToggled() {
        if (_uiState.value.timerState is TimerState.Running) {
            applyAudibility()
        } else {
            val state = _uiState.value
            val slot = audibleSlotFor(state.sessionProgress?.step, state.breakSoundEnabled)
            _uiState.update { it.copy(audibleSlot = slot) }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SetMode -> setMode(event.mode)
            is HomeEvent.ToggleSound -> toggleSound(event.sound)
            is HomeEvent.SetSoundLevel -> setSoundLevel(event.soundId, event.level)
            is HomeEvent.SoundLevelChangeFinished -> persistSoundLevel(event.soundId)
            is HomeEvent.SetPickerSlot -> setPickerSlot(event.slot)
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
            is HomeEvent.ShowPlanEditor -> showPlanEditor()
            is HomeEvent.HidePlanEditor -> _uiState.update { it.copy(planDraft = null) }
            is HomeEvent.SetPlanRowChoice -> editDraft { it.withRowChoice(event.index, event.choice) }
            is HomeEvent.SetPlanStepMinutes -> editDraft { it.withStepMinutes(event.index, event.minutes) }
            is HomeEvent.AddPlanStep -> editDraft { it.withAddedStep() }
            is HomeEvent.SavePlan -> savePlan()
        }
    }

    private fun setMode(mode: AppMode) {
        haptic { click() }

        // Abandon a running plan when switching to Ambient mode
        if (mode == AppMode.AMBIENT && _uiState.value.sessionProgress != null) {
            viewModelScope.launch { sessionRunner.stop() }
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

    /**
     * Only the repository decides. It serializes overlapping toggles under a mutex and
     * refuses to empty the mix; the service hears about the outcome through
     * getActiveMix, never from here. Two rapid deactivations therefore cannot tell the
     * service to drop both sounds while the repository keeps one.
     *
     * The size check below is a UI affordance — it avoids a pointless round-trip and a
     * buzz for a tap the repository would reject — not a correctness guarantee.
     */
    private fun toggleSound(sound: Sound) {
        val slot = _uiState.value.pickerSlot
        val mix = _uiState.value.pickerMix
        val isActive = mix.any { it.sound.id == sound.id }
        if (isActive && mix.size == 1) return
        haptic { heavyClick() }
        viewModelScope.launch {
            soundRepository.setSoundActive(sound.id, active = !isActive, slot = slot)
        }
    }

    /**
     * The same shape as the master volume slider, for the same reason: the level lands
     * in state and on the service on every frame of the drag, so the thumb tracks the
     * finger and the mix is audibly live, but the store is written once, when the finger
     * lifts (see [persistSoundLevel]). Going through the repository per frame put a
     * DataStore edit — serialized behind the repository's mutex — on the drag path, and
     * left the thumb waiting on the round trip back.
     *
     * This is the one thing about the mix the ViewModel tells the service directly, and
     * it is safe where a membership change would not be. Membership carries invariants
     * the repository owns — the mix is never empty, overlapping toggles must not cancel
     * each other — so two writers there could disagree. A level is a last-write-wins
     * scalar on a sound that is already in the mix, and the repository re-asserts the
     * whole mix through getActiveMix the moment the drag ends.
     *
     * Which sounds are active is untouched here, so the palette — a function of exactly
     * that, never of levels — cannot move while a slider does.
     */
    private fun setSoundLevel(soundId: String, level: Float) {
        val clampedLevel = level.coerceIn(0f, 1f)
        val slot = _uiState.value.pickerSlot
        _uiState.update { state ->
            val updated = state.mixFor(slot).map { active ->
                if (active.sound.id == soundId) active.copy(level = clampedLevel) else active
            }
            if (slot == MixSlot.BREAK) state.copy(breakMix = updated) else state.copy(focusMix = updated)
        }
        // A level dragged on the slot that is not sounding must not change the audio.
        if (slot == _uiState.value.audibleSlot) pushMix()
    }

    private fun persistSoundLevel(soundId: String) {
        val slot = _uiState.value.pickerSlot
        val level = _uiState.value.mixFor(slot)
            .firstOrNull { it.sound.id == soundId }
            ?.level
            ?: return
        viewModelScope.launch { soundRepository.setSoundLevel(soundId, level, slot) }
    }

    private fun setPickerSlot(slot: MixSlot) {
        haptic { click() }
        _uiState.update { it.copy(pickerSlot = slot) }
    }

    private fun mixTitle(mix: List<ActiveSound>): String =
        if (mix.size <= 2) {
            mix.joinToString(" + ") { stringProvider.get(it.sound.nameRes) }
        } else {
            stringProvider.getQuantity(R.plurals.mix_sound_count, mix.size, mix.size)
        }

    /**
     * stop() releases every track service-side, and every stop in this ViewModel is
     * followed by a play in normal use (mode switch, reset, timer completion then
     * break). Re-declaring the mix first is what keeps "stop, then play" audible.
     */
    private fun startPlayback() {
        pushMix()
        audioServiceConnection.setVolume(_uiState.value.volume)
        audioServiceConnection.play()
    }

    private fun selectPreset(preset: TimerPreset) {
        haptic { click() }
        _uiState.update { it.copy(selectedPreset = preset) }
        // CUSTOM and PLAN both report 0 focus minutes: neither has a preset
        // duration to remember, and persisting 0 would leave an invalid plan.
        if (preset.focusMinutes > 0) {
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
                    sessionRunner.pause()
                    audioServiceConnection.pause()
                }
                state.timerState is TimerState.Paused -> {
                    sessionRunner.resume()
                    // Not a bare play(): resuming inside a silent break would otherwise
                    // come back playing the focus mix.
                    applyAudibility()
                }
                else -> {
                    // Audio starts when the runner reports the first step, so the
                    // first step and every later one go through the same path.
                    sessionRunner.start(state.planForStart)
                }
            }
        }
    }

    private fun reset() {
        haptic { click() }
        viewModelScope.launch {
            sessionRunner.stop()
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

    private fun showPlanEditor() {
        haptic { click() }
        _uiState.update { it.copy(planDraft = it.sessionPlan) }
    }

    /** Edits apply to the draft only; the stored plan changes on SavePlan. */
    private fun editDraft(edit: (SessionPlan) -> SessionPlan) {
        haptic { tick() }
        _uiState.update { state ->
            val draft = state.planDraft ?: return@update state
            state.copy(planDraft = edit(draft))
        }
    }

    private fun savePlan() {
        val draft = _uiState.value.planDraft ?: return
        if (!draft.isValid) return
        haptic { click() }
        viewModelScope.launch { preferencesRepository.setSessionPlan(draft) }
        _uiState.update { it.copy(planDraft = null) }
    }

    /**
     * The runner decides what happens next; this only makes it audible and
     * records it. A step end always stops the audio: a silent break stays
     * stopped, a sounding one is restarted by the StepStarted that follows.
     */
    private fun onSessionEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.StepStarted -> onStepStarted(event.step)
            is SessionEvent.StepCompleted -> {
                audioServiceConnection.stop()
                chime(chimeRepository.getTimerChimeResource())
                haptic { timerComplete() }
                recordIfFocus(event.step)
            }
            is SessionEvent.PlanCompleted -> {
                audioServiceConnection.stop()
                chime(chimeRepository.getSuccessChimeResource())
                haptic { timerComplete() }
                recordIfFocus(event.lastStep)
            }
        }
    }

    private fun onStepStarted(step: PlanStep) = applyAudibility(step)

    /**
     * Decides which slot is audible from the step and the toggle, then makes the
     * audio match. Called with the step on every StepStarted, and with the step from
     * progress when a pause is resumed or the toggle flips mid-run, so a break can go
     * from silent to sounding without waiting for the next step. A null step — nothing
     * in progress — resolves to FOCUS and issues no audio call at all, which is also
     * how a finished plan hands the slot back.
     */
    private fun applyAudibility(step: PlanStep? = _uiState.value.sessionProgress?.step) {
        val slot = audibleSlotFor(step, _uiState.value.breakSoundEnabled)
        _uiState.update { it.copy(audibleSlot = slot) }
        when {
            step == null -> Unit
            step is PlanStep.Focus -> startPlayback()
            slot == MixSlot.BREAK -> startPlayback()
            else -> audioServiceConnection.stop()
        }
    }

    /** The whole rule: BREAK only during a break the user asked to hear. */
    private fun audibleSlotFor(step: PlanStep?, breakSoundEnabled: Boolean): MixSlot =
        if (step is PlanStep.Break && breakSoundEnabled) MixSlot.BREAK else MixSlot.FOCUS

    private fun chime(resource: Int) {
        if (_uiState.value.chimeEnabled) chimePlayer.playChime(resource)
    }

    /** A completed focus step is a session against the whole mix, as before. */
    private fun recordIfFocus(step: PlanStep) {
        if (step !is PlanStep.Focus) return
        val mix = _uiState.value.focusMix
        if (mix.isEmpty()) return
        viewModelScope.launch {
            saveSessionUseCase(
                soundId = MixCodec.encode(mix, withLevels = false),
                durationMinutes = step.minutes,
                wasCompleted = true
            )
        }
    }

    private fun haptic(action: HapticManager.() -> Unit) {
        if (_uiState.value.hapticsEnabled) hapticManager.action()
    }
}
