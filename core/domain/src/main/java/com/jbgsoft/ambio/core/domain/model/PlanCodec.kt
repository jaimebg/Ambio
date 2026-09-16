package com.jbgsoft.ambio.core.domain.model

/**
 * The storage format for a session plan, one string in DataStore.
 *
 *     F25,B5,F25,B5,F35,B15;loop
 *     F25,B5;end
 *
 * Reading is tolerant for the same reason [MixCodec] is: a stored string that
 * some later version cannot fully read must degrade to something playable, never
 * to a crash or an unstartable plan.
 */
object PlanCodec {

    private const val LOOP = "loop"
    private const val END = "end"

    fun encode(plan: SessionPlan): String {
        val body = plan.steps.joinToString(",") { step ->
            val letter = if (step is PlanStep.Focus) 'F' else 'B'
            "$letter${step.minutes}"
        }
        return "$body;${if (plan.repeat) LOOP else END}"
    }

    /** Never returns an invalid plan: anything unusable falls back to [SessionPlan.DEFAULT]. */
    fun decode(encoded: String): SessionPlan {
        val body = encoded.substringBefore(';')
        val suffix = encoded.substringAfter(';', "").trim()

        val steps = body.split(',')
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                if (trimmed.length < 2) return@mapNotNull null
                val minutes = trimmed.drop(1).toIntOrNull() ?: return@mapNotNull null
                when (trimmed[0].uppercaseChar()) {
                    'F' -> PlanStep.Focus(minutes.coerceIn(PlanStep.MIN_MINUTES, PlanStep.MAX_FOCUS_MINUTES))
                    'B' -> PlanStep.Break(minutes.coerceIn(PlanStep.MIN_MINUTES, PlanStep.MAX_BREAK_MINUTES))
                    else -> null
                }
            }
            .take(SessionPlan.MAX_STEPS)

        val plan = SessionPlan(steps, repeat = suffix.equals(LOOP, ignoreCase = true))
        return if (plan.isValid) plan else SessionPlan.DEFAULT
    }
}
