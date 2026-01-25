package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.TimerState
import kotlinx.coroutines.flow.Flow

interface TimerRepository {
    val timerState: Flow<TimerState>
    suspend fun startTimer(durationMs: Long)
    suspend fun pauseTimer()
    suspend fun resumeTimer()
    suspend fun resetTimer()
    suspend fun startBreak(durationMs: Long)
}
