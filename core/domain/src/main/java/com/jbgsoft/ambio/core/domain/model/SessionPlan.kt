package com.jbgsoft.ambio.core.domain.model

/**
 * One slot of a session plan. "Loop from here" and "End it here" are not steps:
 * they are the plan's terminator, see [SessionPlan.repeat].
 */
sealed interface PlanStep {
    val minutes: Int

    data class Focus(override val minutes: Int) : PlanStep
    data class Break(override val minutes: Int) : PlanStep

    /** The largest number of minutes this kind of step may hold. */
    val maxMinutes: Int
        get() = when (this) {
            is Focus -> MAX_FOCUS_MINUTES
            is Break -> MAX_BREAK_MINUTES
        }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_FOCUS_MINUTES = 120
        const val MAX_BREAK_MINUTES = 60
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
    }
}

/**
 * An ordered list of steps and how it ends: [repeat] loops back to the first
 * step forever, otherwise the plan stops after the last step.
 */
data class SessionPlan(
    val steps: List<PlanStep>,
    val repeat: Boolean
) {
    val isValid: Boolean
        get() = steps.size in 1..MAX_STEPS &&
            steps.any { it is PlanStep.Focus } &&
            steps.all { it.minutes in PlanStep.MIN_MINUTES..it.maxMinutes }

    val focusCount: Int get() = steps.count { it is PlanStep.Focus }

    /** 1-based position of the focus step at or before [stepIndex] among the plan's focus steps. */
    fun focusOrdinal(stepIndex: Int): Int =
        steps.take(stepIndex + 1).count { it is PlanStep.Focus }

    companion object {
        const val MAX_STEPS = 12

        val DEFAULT = quick(PlanStep.DEFAULT_FOCUS_MINUTES, PlanStep.DEFAULT_BREAK_MINUTES)

        /** The two-step plan the quick presets (25, 50, Custom) run as. */
        fun quick(focusMinutes: Int, breakMinutes: Int) = SessionPlan(
            steps = listOf(PlanStep.Focus(focusMinutes), PlanStep.Break(breakMinutes)),
            repeat = false
        )
    }
}
