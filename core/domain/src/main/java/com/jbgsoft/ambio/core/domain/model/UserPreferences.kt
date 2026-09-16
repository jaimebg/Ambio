package com.jbgsoft.ambio.core.domain.model

data class UserPreferences(
    // Session state — where the user left off
    val lastMix: String = "rain",
    /** The break mix, encoded like [lastMix]. Null until the user edits it: readers fall back to [lastMix]. */
    val breakMix: String? = null,
    val volume: Float = 0.7f,
    val lastTimerMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val sessionPlan: SessionPlan = SessionPlan.DEFAULT,
    val lastMode: AppMode = AppMode.TIMER,
    // Preferences — how the user wants the app to behave
    val hapticsEnabled: Boolean = true,
    val chimeEnabled: Boolean = true,
    val effectsEnabled: Boolean = true,
    val breakSoundEnabled: Boolean = false
)
