package com.jbgsoft.ambio.core.domain.model

sealed class TimerState {
    data object Idle : TimerState()

    data class Running(
        val remainingMs: Long,
        val totalMs: Long,
        val isBreak: Boolean = false
    ) : TimerState() {
        val progress: Float get() = remainingMs.toFloat() / totalMs.toFloat()
    }

    data class Paused(
        val remainingMs: Long,
        val totalMs: Long
    ) : TimerState()

    data class Completed(val wasBreak: Boolean = false) : TimerState()
}
