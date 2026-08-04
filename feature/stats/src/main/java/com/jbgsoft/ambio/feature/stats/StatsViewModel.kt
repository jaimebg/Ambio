package com.jbgsoft.ambio.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.usecase.DeleteSessionUseCase
import com.jbgsoft.ambio.core.domain.usecase.GetSessionHistoryUseCase
import com.jbgsoft.ambio.core.domain.usecase.GetSessionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getSessionStats: GetSessionStatsUseCase,
    private val getSessionHistory: GetSessionHistoryUseCase,
    private val deleteSession: DeleteSessionUseCase,
    private val soundRepository: SoundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(getSessionStats(), getSessionHistory()) { stats, sessions ->
                StatsUiState(
                    totalFocusMinutes = stats.totalFocusMinutes,
                    completedSessionCount = stats.completedSessionCount,
                    sessions = sessions.map { session ->
                        SessionRow(
                            id = session.id,
                            soundNameRes = soundRepository.getSoundById(session.soundId)?.nameRes,
                            durationMinutes = session.durationMinutes,
                            completedAt = session.completedAt
                        )
                    }
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onDeleteSession(id: Long) {
        viewModelScope.launch { deleteSession(id) }
    }
}
