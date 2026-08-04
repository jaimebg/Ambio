package com.jbgsoft.ambio.feature.home

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState

data class HomeUiState(
    val mode: AppMode = AppMode.TIMER,
    val activeMix: List<ActiveSound> = emptyList(),
    val availableSounds: List<Sound> = emptyList(),
    val timerState: TimerState = TimerState.Idle,
    val selectedPreset: TimerPreset = TimerPreset.FOCUS_25,
    val customMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val volume: Float = 0.7f,
    val isPlaying: Boolean = false,
    val showSoundPicker: Boolean = false,
    val isServiceConnected: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true
)
