package com.jbgsoft.ambio.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionPlanTest {

    private fun plan(vararg steps: PlanStep, repeat: Boolean = false) =
        SessionPlan(steps.toList(), repeat)

    @Test
    fun `the default plan is one focus and one break, ending`() {
        assertThat(SessionPlan.DEFAULT.steps)
            .containsExactly(PlanStep.Focus(25), PlanStep.Break(5)).inOrder()
        assertThat(SessionPlan.DEFAULT.repeat).isFalse()
        assertThat(SessionPlan.DEFAULT.isValid).isTrue()
    }

    @Test
    fun `quick builds the two-step plan the presets use`() {
        val quick = SessionPlan.quick(focusMinutes = 50, breakMinutes = 10)

        assertThat(quick.steps).containsExactly(PlanStep.Focus(50), PlanStep.Break(10)).inOrder()
        assertThat(quick.repeat).isFalse()
    }

    @Test
    fun `a plan with no focus step is invalid`() {
        assertThat(plan(PlanStep.Break(5)).isValid).isFalse()
    }

    @Test
    fun `an empty plan is invalid`() {
        assertThat(plan().isValid).isFalse()
    }

    @Test
    fun `more than twelve steps is invalid`() {
        val steps = List(13) { PlanStep.Focus(1) }
        assertThat(SessionPlan(steps, repeat = false).isValid).isFalse()
        assertThat(SessionPlan(steps.take(12), repeat = false).isValid).isTrue()
    }

    @Test
    fun `focus minutes must sit in one to one hundred and twenty`() {
        assertThat(plan(PlanStep.Focus(0)).isValid).isFalse()
        assertThat(plan(PlanStep.Focus(121)).isValid).isFalse()
        assertThat(plan(PlanStep.Focus(120)).isValid).isTrue()
    }

    @Test
    fun `break minutes must sit in one to sixty`() {
        assertThat(plan(PlanStep.Focus(1), PlanStep.Break(0)).isValid).isFalse()
        assertThat(plan(PlanStep.Focus(1), PlanStep.Break(61)).isValid).isFalse()
        assertThat(plan(PlanStep.Focus(1), PlanStep.Break(60)).isValid).isTrue()
    }

    @Test
    fun `focusOrdinal counts focus steps up to and including the index`() {
        val p = plan(PlanStep.Focus(25), PlanStep.Break(5), PlanStep.Focus(25), PlanStep.Break(5), PlanStep.Focus(35))

        assertThat(p.focusCount).isEqualTo(3)
        assertThat(p.focusOrdinal(0)).isEqualTo(1)
        assertThat(p.focusOrdinal(1)).isEqualTo(1)
        assertThat(p.focusOrdinal(2)).isEqualTo(2)
        assertThat(p.focusOrdinal(4)).isEqualTo(3)
    }
}
