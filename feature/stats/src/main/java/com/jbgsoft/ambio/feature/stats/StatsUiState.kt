package com.jbgsoft.ambio.feature.stats

import androidx.annotation.StringRes

data class SessionRow(
    val id: Long,
    @param:StringRes val soundNameRes: Int?,
    val durationMinutes: Int,
    val completedAt: Long,
    val wasCompleted: Boolean
)

data class StatsUiState(
    val totalFocusMinutes: Int = 0,
    val completedSessionCount: Int = 0,
    val sessions: List<SessionRow> = emptyList()
)
