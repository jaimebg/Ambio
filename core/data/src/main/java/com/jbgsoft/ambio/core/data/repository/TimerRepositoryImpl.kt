package com.jbgsoft.ambio.core.data.repository

import com.jbgsoft.ambio.core.common.di.DefaultDispatcher
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TimerRepository that manages countdown timer state.
 *
 * Uses coroutines for countdown logic with 1-second tick intervals.
 * The dispatcher can be injected for testing purposes.
 *
 * @param dispatcher The coroutine dispatcher to use for timer operations.
 *                   Uses Dispatchers.Default (via @DefaultDispatcher) in production.
 */
@Singleton
class TimerRepositoryImpl @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : TimerRepository {

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    override val timerState: Flow<TimerState> = _timerState.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(dispatcher)

    private var totalMs: Long = 0
    private var remainingMs: Long = 0
    private var isBreak: Boolean = false

    override suspend fun startTimer(durationMs: Long) {
        stopTimerJob()
        totalMs = durationMs
        remainingMs = durationMs
        isBreak = false
        startCountdown()
    }

    override suspend fun pauseTimer() {
        val currentState = _timerState.value
        if (currentState is TimerState.Running) {
            stopTimerJob()
            remainingMs = currentState.remainingMs
            _timerState.value = TimerState.Paused(
                remainingMs = remainingMs,
                totalMs = totalMs
            )
        }
    }

    override suspend fun resumeTimer() {
        val currentState = _timerState.value
        if (currentState is TimerState.Paused) {
            remainingMs = currentState.remainingMs
            startCountdown()
        }
    }

    override suspend fun resetTimer() {
        stopTimerJob()
        _timerState.value = TimerState.Idle
        totalMs = 0
        remainingMs = 0
        isBreak = false
    }

    override suspend fun startBreak(durationMs: Long) {
        stopTimerJob()
        totalMs = durationMs
        remainingMs = durationMs
        isBreak = true
        startCountdown()
    }

    private fun startCountdown() {
        timerJob = scope.launch {
            _timerState.value = TimerState.Running(
                remainingMs = remainingMs,
                totalMs = totalMs,
                isBreak = isBreak
            )

            while (remainingMs > 0) {
                delay(1000)
                remainingMs -= 1000
                if (remainingMs >= 0) {
                    _timerState.value = TimerState.Running(
                        remainingMs = remainingMs.coerceAtLeast(0),
                        totalMs = totalMs,
                        isBreak = isBreak
                    )
                }
            }

            _timerState.value = TimerState.Completed
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel()
        timerJob = null
    }
}
