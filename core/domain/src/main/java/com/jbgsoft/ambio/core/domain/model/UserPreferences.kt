package com.jbgsoft.ambio.core.domain.model

data class UserPreferences(
    val lastSoundId: String = "rain",
    val volume: Float = 0.7f,
    val lastTimerMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val lastMode: AppMode = AppMode.TIMER
)
