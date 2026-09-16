package com.jbgsoft.ambio.feature.home

import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.MixSlot
import com.jbgsoft.ambio.core.domain.model.PlanRowChoice
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset

sealed class HomeEvent {
    data class SetMode(val mode: AppMode) : HomeEvent()
    data class ToggleSound(val sound: Sound) : HomeEvent()
    data class SetSoundLevel(val soundId: String, val level: Float) : HomeEvent()
    data class SoundLevelChangeFinished(val soundId: String) : HomeEvent()
    data class SetPickerSlot(val slot: MixSlot) : HomeEvent()
    data class SelectPreset(val preset: TimerPreset) : HomeEvent()
    data class SetCustomMinutes(val minutes: Int) : HomeEvent()
    data object CustomMinutesChangeFinished : HomeEvent()
    data class SetBreakMinutes(val minutes: Int) : HomeEvent()
    data object BreakMinutesChangeFinished : HomeEvent()
    data class SetSleepMinutes(val minutes: Int) : HomeEvent()
    data class SetVolume(val volume: Float) : HomeEvent()
    data object VolumeChangeFinished : HomeEvent()
    data object PlayPause : HomeEvent()
    data object Reset : HomeEvent()
    data object ShowSoundPicker : HomeEvent()
    data object HideSoundPicker : HomeEvent()
    data object ShowPlanEditor : HomeEvent()
    data object HidePlanEditor : HomeEvent()
    data class SetPlanRowChoice(val index: Int, val choice: PlanRowChoice) : HomeEvent()
    data class SetPlanStepMinutes(val index: Int, val minutes: Int) : HomeEvent()
    data object AddPlanStep : HomeEvent()
    data object SavePlan : HomeEvent()
}
