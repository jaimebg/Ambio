package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import javax.inject.Inject

class SaveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        soundId: String,
        durationMinutes: Int,
        wasCompleted: Boolean
    ) {
        val session = Session(
            soundId = soundId,
            durationMinutes = durationMinutes,
            completedAt = System.currentTimeMillis(),
            wasCompleted = wasCompleted
        )
        sessionRepository.saveSession(session)
    }
}
