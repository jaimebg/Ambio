package com.jbgsoft.ambio.core.domain.session

import com.jbgsoft.ambio.core.domain.model.SessionPlan
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Runs a [SessionPlan] step by step on top of TimerRepository. The only thing in
 * Timer mode that starts or stops the timer; the ViewModel reacts to [events].
 */
interface SessionRunner {
    /** Null when no plan is running. Survives ViewModel recreation. */
    val progress: StateFlow<SessionProgress?>

    /** Replay 0: an event fires once, for whoever is listening at the time. */
    val events: SharedFlow<SessionEvent>

    /** Starts [plan] from its first step, abandoning any plan in progress. Requires a valid plan. */
    suspend fun start(plan: SessionPlan)
    suspend fun pause()
    suspend fun resume()

    /** Abandons the plan: progress becomes null and the timer returns to Idle. Emits nothing. */
    suspend fun stop()
}
