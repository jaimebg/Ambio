package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import javax.inject.Inject

class PauseTimerUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke() {
        timerRepository.pauseTimer()
    }
}
