package com.jbgsoft.ambio.core.domain.repository

import com.jbgsoft.ambio.core.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getAllSessions(): Flow<List<Session>>
    suspend fun saveSession(session: Session)
    suspend fun deleteSession(sessionId: Long)
    fun getTotalFocusMinutes(): Flow<Int>
    fun getCompletedSessionCount(): Flow<Int>
}
