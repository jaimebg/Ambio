package com.jbgsoft.ambio.core.domain.model

/**
 * What the dropdown on an editor row offers. The editor shows one row per step
 * plus a terminator row at index `steps.size`; LOOP and END on any row make that
 * row the terminator and discard the rows after it.
 */
enum class PlanRowChoice { FOCUS, BREAK, LOOP, END }

fun SessionPlan.withRowChoice(index: Int, choice: PlanRowChoice): SessionPlan = when (choice) {
    PlanRowChoice.LOOP -> copy(steps = steps.take(index), repeat = true)
    PlanRowChoice.END -> copy(steps = steps.take(index), repeat = false)
    PlanRowChoice.FOCUS, PlanRowChoice.BREAK -> {
        val isFocus = choice == PlanRowChoice.FOCUS
        when {
            index < steps.size -> {
                val minutes = steps[index].minutes
                copy(steps = steps.toMutableList().also { it[index] = stepOf(isFocus, minutes) })
            }
            steps.size >= SessionPlan.MAX_STEPS -> this
            else -> {
                val minutes = if (isFocus) PlanStep.DEFAULT_FOCUS_MINUTES else PlanStep.DEFAULT_BREAK_MINUTES
                copy(steps = steps + stepOf(isFocus, minutes))
            }
        }
    }
}

fun SessionPlan.withStepMinutes(index: Int, minutes: Int): SessionPlan {
    if (index !in steps.indices) return this
    val step = steps[index]
    return copy(steps = steps.toMutableList().also {
        it[index] = stepOf(step is PlanStep.Focus, minutes)
    })
}

/** Appends a step of the opposite kind to the last one, so Focus and Break alternate. */
fun SessionPlan.withAddedStep(): SessionPlan {
    if (steps.size >= SessionPlan.MAX_STEPS) return this
    val addFocus = steps.lastOrNull() !is PlanStep.Focus
    val minutes = if (addFocus) PlanStep.DEFAULT_FOCUS_MINUTES else PlanStep.DEFAULT_BREAK_MINUTES
    return copy(steps = steps + stepOf(addFocus, minutes))
}

private fun stepOf(isFocus: Boolean, minutes: Int): PlanStep = if (isFocus) {
    PlanStep.Focus(minutes.coerceIn(PlanStep.MIN_MINUTES, PlanStep.MAX_FOCUS_MINUTES))
} else {
    PlanStep.Break(minutes.coerceIn(PlanStep.MIN_MINUTES, PlanStep.MAX_BREAK_MINUTES))
}
