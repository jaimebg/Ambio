package com.jbgsoft.ambio.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jbgsoft.ambio.core.domain.model.Session

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val soundId: String,
    val durationMinutes: Int,
    val completedAt: Long,
    val wasCompleted: Boolean
) {
    fun toDomain(): Session = Session(
        id = id,
        soundId = soundId,
        durationMinutes = durationMinutes,
        completedAt = completedAt,
        wasCompleted = wasCompleted
    )

    companion object {
        fun fromDomain(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            soundId = session.soundId,
            durationMinutes = session.durationMinutes,
            completedAt = session.completedAt,
            wasCompleted = session.wasCompleted
        )
    }
}
