package com.jbgsoft.ambio.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionPlanEditingTest {

    private val six = SessionPlan(
        listOf(
            PlanStep.Focus(25), PlanStep.Break(5),
            PlanStep.Focus(25), PlanStep.Break(5),
            PlanStep.Focus(35), PlanStep.Break(15)
        ),
        repeat = false
    )

    @Test
    fun `choosing loop on a row drops the rows after it and sets repeat`() {
        val edited = six.withRowChoice(index = 3, choice = PlanRowChoice.LOOP)

        assertThat(edited.steps).isEqualTo(six.steps.take(3))
        assertThat(edited.repeat).isTrue()
    }

    @Test
    fun `choosing end on a row drops the rows after it and clears repeat`() {
        val edited = six.copy(repeat = true).withRowChoice(index = 2, choice = PlanRowChoice.END)

        assertThat(edited.steps).isEqualTo(six.steps.take(2))
        assertThat(edited.repeat).isFalse()
    }

    @Test
    fun `choosing loop on the terminator row keeps every step`() {
        val edited = six.withRowChoice(index = six.steps.size, choice = PlanRowChoice.LOOP)

        assertThat(edited.steps).isEqualTo(six.steps)
        assertThat(edited.repeat).isTrue()
    }

    @Test
    fun `changing a step's type keeps its minutes clamped to the new range`() {
        val edited = SessionPlan(listOf(PlanStep.Focus(90)), repeat = false)
            .withRowChoice(index = 0, choice = PlanRowChoice.BREAK)

        assertThat(edited.steps).containsExactly(PlanStep.Break(60))
    }

    @Test
    fun `choosing a type on the terminator row appends a step with the default minutes`() {
        val edited = six.withRowChoice(index = six.steps.size, choice = PlanRowChoice.FOCUS)

        assertThat(edited.steps).hasSize(7)
        assertThat(edited.steps.last()).isEqualTo(PlanStep.Focus(25))
    }

    @Test
    fun `choosing a type on the terminator row of a full plan changes nothing`() {
        val full = SessionPlan(List(12) { PlanStep.Focus(1) }, repeat = false)

        assertThat(full.withRowChoice(index = 12, choice = PlanRowChoice.BREAK)).isEqualTo(full)
    }

    @Test
    fun `withStepMinutes clamps into the range of that step's type`() {
        val edited = six.withStepMinutes(index = 1, minutes = 999)

        assertThat(edited.steps[1]).isEqualTo(PlanStep.Break(60))
        assertThat(six.withStepMinutes(index = 0, minutes = 0).steps[0]).isEqualTo(PlanStep.Focus(1))
    }

    @Test
    fun `withAddedStep alternates the type of the previous step`() {
        val afterBreak = six.withAddedStep()
        assertThat(afterBreak.steps.last()).isEqualTo(PlanStep.Focus(25))

        val afterFocus = afterBreak.withAddedStep()
        assertThat(afterFocus.steps.last()).isEqualTo(PlanStep.Break(5))
    }

    @Test
    fun `withAddedStep on an empty plan adds a focus step`() {
        val edited = SessionPlan(emptyList(), repeat = false).withAddedStep()

        assertThat(edited.steps).containsExactly(PlanStep.Focus(25))
    }

    @Test
    fun `withAddedStep on a full plan changes nothing`() {
        val full = SessionPlan(List(12) { PlanStep.Focus(1) }, repeat = false)

        assertThat(full.withAddedStep()).isEqualTo(full)
    }
}
