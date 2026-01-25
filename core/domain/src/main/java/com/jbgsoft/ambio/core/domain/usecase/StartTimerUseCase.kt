package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import javax.inject.Inject

class StartTimerUseCase @Inject constructor(
    private val timerRepository: TimerRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(minutes: Int) {
        val durationMs = minutes * 60 * 1000L
        timerRepository.startTimer(durationMs)
        preferencesRepository.setLastTimerMinutes(minutes)
    }
}
