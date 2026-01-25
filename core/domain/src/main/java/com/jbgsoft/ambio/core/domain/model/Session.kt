package com.jbgsoft.ambio.core.domain.model

data class Session(
    val id: Long = 0,
    val soundId: String,
    val durationMinutes: Int,
    val completedAt: Long,
    val wasCompleted: Boolean
)
