package com.jbgsoft.ambio.core.domain.session

import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan

/**
 * Where a running plan is. The current step's countdown is not in here:
 * TimerRepository already publishes it and the dial reads it from there.
 */
data class SessionProgress(
    val plan: SessionPlan,
    val stepIndex: Int,
    /** 0 on the first pass; only a looping plan ever goes higher. */
    val lap: Int
) {
    val step: PlanStep get() = plan.steps[stepIndex]

    /** 1-based: "Focus 2 of 3". */
    val focusOrdinal: Int get() = plan.focusOrdinal(stepIndex)
    val focusCount: Int get() = plan.focusCount
}
