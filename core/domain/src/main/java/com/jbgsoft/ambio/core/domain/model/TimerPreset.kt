package com.jbgsoft.ambio.core.domain.model

enum class TimerPreset(
    val displayName: String,
    val focusMinutes: Int,
    val breakMinutes: Int
) {
    FOCUS_25("25 min", 25, 5),
    FOCUS_50("50 min", 50, 10),
    CUSTOM("Custom", 0, 0)
}
