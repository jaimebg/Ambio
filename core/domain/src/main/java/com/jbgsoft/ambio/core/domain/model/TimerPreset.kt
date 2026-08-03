package com.jbgsoft.ambio.core.domain.model

enum class TimerPreset(
    val focusMinutes: Int,
    val breakMinutes: Int
) {
    FOCUS_25(25, 5),
    FOCUS_50(50, 10),
    CUSTOM(0, 0)
}
