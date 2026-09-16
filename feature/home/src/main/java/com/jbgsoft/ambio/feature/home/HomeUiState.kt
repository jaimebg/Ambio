package com.jbgsoft.ambio.feature.home

import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.MixSlot
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.session.SessionProgress

data class HomeUiState(
    val mode: AppMode = AppMode.TIMER,
    val focusMix: List<ActiveSound> = emptyList(),
    val breakMix: List<ActiveSound> = emptyList(),
    /** The slot that plays, or would play, right now: BREAK only during a break with break sound on. */
    val audibleSlot: MixSlot = MixSlot.FOCUS,
    /** The slot the picker edits. Independent of [audibleSlot]: a break mix can be prepared during focus. */
    val pickerSlot: MixSlot = MixSlot.FOCUS,
    val availableSounds: List<Sound> = emptyList(),
    val timerState: TimerState = TimerState.Idle,
    val sessionProgress: SessionProgress? = null,
    val selectedPreset: TimerPreset = TimerPreset.FOCUS_25,
    val customMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val sessionPlan: SessionPlan = SessionPlan.DEFAULT,
    val planDraft: SessionPlan? = null,
    val volume: Float = 0.7f,
    val isPlaying: Boolean = false,
    val showSoundPicker: Boolean = false,
    val isServiceConnected: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true,
    val breakSoundEnabled: Boolean = false
) {
    fun mixFor(slot: MixSlot): List<ActiveSound> =
        if (slot == MixSlot.BREAK) breakMix else focusMix

    /** What sounds now, so the gradient, particles and mix bar show what plays. */
    val activeMix: List<ActiveSound> get() = mixFor(audibleSlot)

    /** What the picker shows. */
    val pickerMix: List<ActiveSound> get() = mixFor(pickerSlot)

    /** What pressing play would run: the quick presets are two-step plans, PLAN is the stored one. */
    val planForStart: SessionPlan
        get() = when (selectedPreset) {
            TimerPreset.FOCUS_25 -> SessionPlan.quick(TimerPreset.FOCUS_25.focusMinutes, breakMinutes)
            TimerPreset.FOCUS_50 -> SessionPlan.quick(TimerPreset.FOCUS_50.focusMinutes, breakMinutes)
            TimerPreset.CUSTOM -> SessionPlan.quick(customMinutes, breakMinutes)
            TimerPreset.PLAN -> sessionPlan
        }

    /** The minutes the dial shows while idle: the first step of what would start. */
    val selectedMinutes: Int
        get() = planForStart.steps.firstOrNull()?.minutes ?: PlanStep.DEFAULT_FOCUS_MINUTES
}
