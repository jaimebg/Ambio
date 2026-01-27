package com.jbgsoft.ambio.feature.home

import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.TimerPreset

sealed class HomeEvent {
    data class SetMode(val mode: AppMode) : HomeEvent()
    data class SelectSound(val sound: Sound) : HomeEvent()
    data class SelectPreset(val preset: TimerPreset) : HomeEvent()
    data class SetCustomMinutes(val minutes: Int) : HomeEvent()
    data class SetVolume(val volume: Float) : HomeEvent()
    data object VolumeChangeFinished : HomeEvent()
    data object PlayPause : HomeEvent()
    data object Reset : HomeEvent()
    data object ShowSoundPicker : HomeEvent()
    data object HideSoundPicker : HomeEvent()
    data object TimerCompleted : HomeEvent()
}
