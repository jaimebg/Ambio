package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import javax.inject.Inject

class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: Long) = sessionRepository.deleteSession(sessionId)
}
