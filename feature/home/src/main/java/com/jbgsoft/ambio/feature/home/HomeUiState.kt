package com.jbgsoft.ambio.feature.home

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.session.SessionProgress

data class HomeUiState(
    val mode: AppMode = AppMode.TIMER,
    val activeMix: List<ActiveSound> = emptyList(),
    val availableSounds: List<Sound> = emptyList(),
    val timerState: TimerState = TimerState.Idle,
    val sessionProgress: SessionProgress? = null,
    val selectedPreset: TimerPreset = TimerPreset.FOCUS_25,
    val customMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val sessionPlan: SessionPlan = SessionPlan.DEFAULT,
    val volume: Float = 0.7f,
    val isPlaying: Boolean = false,
    val showSoundPicker: Boolean = false,
    val isServiceConnected: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true
) {
    /** What pressing play would run: the quick presets are two-step plans, PLAN is the stored one. */
    val planForStart: SessionPlan
        get() = when (selectedPreset) {
            TimerPreset.FOCUS_25 -> SessionPlan.quick(25, breakMinutes)
            TimerPreset.FOCUS_50 -> SessionPlan.quick(50, breakMinutes)
            TimerPreset.CUSTOM -> SessionPlan.quick(customMinutes, breakMinutes)
            TimerPreset.PLAN -> sessionPlan
        }

    /** The minutes the dial shows while idle: the first step of what would start. */
    val selectedMinutes: Int get() = planForStart.steps.first().minutes
}
