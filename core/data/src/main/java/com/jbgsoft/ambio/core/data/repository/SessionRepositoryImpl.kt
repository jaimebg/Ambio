package com.jbgsoft.ambio.core.data.repository

import com.jbgsoft.ambio.core.data.local.dao.SessionDao
import com.jbgsoft.ambio.core.data.local.entity.SessionEntity
import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override fun getAllSessions(): Flow<List<Session>> =
        sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveSession(session: Session) {
        sessionDao.insertSession(SessionEntity.fromDomain(session))
    }

    override suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSession(sessionId)
    }

    override fun getTotalFocusMinutes(): Flow<Int> =
        sessionDao.getTotalFocusMinutes()

    override fun getCompletedSessionCount(): Flow<Int> =
        sessionDao.getCompletedSessionCount()
}
