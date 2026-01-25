package com.jbgsoft.ambio.core.domain.usecase

import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class SessionStats(
    val totalFocusMinutes: Int,
    val completedSessionCount: Int
)

class GetSessionStatsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<SessionStats> = combine(
        sessionRepository.getTotalFocusMinutes(),
        sessionRepository.getCompletedSessionCount()
    ) { totalMinutes, completedCount ->
        SessionStats(
            totalFocusMinutes = totalMinutes,
            completedSessionCount = completedCount
        )
    }
}
