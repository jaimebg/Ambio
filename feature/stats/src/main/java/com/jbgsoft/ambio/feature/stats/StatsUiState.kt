package com.jbgsoft.ambio.feature.stats

data class SessionRow(
    val id: Long,
    // One entry per recorded sound; null where that sound no longer exists.
    val soundNameResIds: List<Int?>,
    val durationMinutes: Int,
    val completedAt: Long
)

data class StatsUiState(
    val totalFocusMinutes: Int = 0,
    val completedSessionCount: Int = 0,
    val sessions: List<SessionRow> = emptyList()
)
