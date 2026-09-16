package com.jbgsoft.ambio.core.data.session

import com.jbgsoft.ambio.core.common.di.DefaultDispatcher
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import com.jbgsoft.ambio.core.domain.session.SessionEvent
import com.jbgsoft.ambio.core.domain.session.SessionProgress
import com.jbgsoft.ambio.core.domain.session.SessionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advances on every [TimerState.Completed] the repository emits. Nothing else
 * sets the timer Running after a Completed, so the StateFlow cannot conflate the
 * Completed away before this collector sees it. The mutex serializes advance()
 * against start() and stop(): a stop that lands during an advance either wins
 * (progress null, advance returns early) or is applied right after it.
 */
@Singleton
class SessionRunnerImpl @Inject constructor(
    private val timerRepository: TimerRepository,
    @param:DefaultDispatcher dispatcher: CoroutineDispatcher
) : SessionRunner {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<SessionProgress?>(null)
    override val progress: StateFlow<SessionProgress?> = _progress.asStateFlow()

    // Three events per step change at most; 64 is headroom, not a budget.
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            timerRepository.timerState.collect { state ->
                if (state is TimerState.Completed) advance()
            }
        }
    }

    override suspend fun start(plan: SessionPlan) {
        require(plan.isValid) { "cannot start an invalid plan: $plan" }
        mutex.withLock {
            val first = SessionProgress(plan = plan, stepIndex = 0, lap = 0)
            _progress.value = first
            launchStep(first)
        }
    }

    override suspend fun pause() = timerRepository.pauseTimer()

    override suspend fun resume() = timerRepository.resumeTimer()

    override suspend fun stop() = mutex.withLock {
        _progress.value = null
        timerRepository.resetTimer()
    }

    private suspend fun advance() = mutex.withLock {
        val current = _progress.value ?: return
        val plan = current.plan
        val isLast = current.stepIndex == plan.steps.lastIndex

        if (isLast && !plan.repeat) {
            _progress.value = null
            timerRepository.resetTimer()
            _events.emit(SessionEvent.PlanCompleted(current.step))
            return
        }

        _events.emit(SessionEvent.StepCompleted(current.step, current.stepIndex))
        val next = if (isLast) {
            current.copy(stepIndex = 0, lap = current.lap + 1)
        } else {
            current.copy(stepIndex = current.stepIndex + 1)
        }
        _progress.value = next
        launchStep(next)
    }

    private suspend fun launchStep(progress: SessionProgress) {
        val step = progress.step
        val durationMs = step.minutes * 60_000L
        when (step) {
            is PlanStep.Focus -> timerRepository.startTimer(durationMs)
            is PlanStep.Break -> timerRepository.startBreak(durationMs)
        }
        _events.emit(SessionEvent.StepStarted(step, progress.stepIndex))
    }
}
