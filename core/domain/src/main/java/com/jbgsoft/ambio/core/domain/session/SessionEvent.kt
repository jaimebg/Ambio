package com.jbgsoft.ambio.core.domain.session

import com.jbgsoft.ambio.core.domain.model.PlanStep

sealed interface SessionEvent {
    data class StepStarted(val step: PlanStep, val stepIndex: Int) : SessionEvent

    /** A step ended and the plan goes on. Not emitted for the last step of an ending plan. */
    data class StepCompleted(val step: PlanStep, val stepIndex: Int) : SessionEvent

    /** The last step of a non-looping plan ended. Emitted instead of [StepCompleted] for that step. */
    data class PlanCompleted(val lastStep: PlanStep) : SessionEvent
}
