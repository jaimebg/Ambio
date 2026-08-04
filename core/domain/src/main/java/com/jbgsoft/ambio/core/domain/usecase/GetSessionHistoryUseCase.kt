package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<List<Session>> = sessionRepository.getAllSessions()
}
