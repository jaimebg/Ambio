package com.jbgsoft.ambio.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jbgsoft.ambio.core.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY completedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM sessions WHERE wasCompleted = 1")
    fun getTotalFocusMinutes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sessions WHERE wasCompleted = 1")
    fun getCompletedSessionCount(): Flow<Int>
}
