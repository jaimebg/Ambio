package com.jbgsoft.ambio.feature.home.components

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import org.junit.Test

class PlanSummaryTest {

    @Test
    fun `a looping plan lists its minutes and ends with the loop glyph`() {
        val plan = SessionPlan(
            listOf(PlanStep.Focus(25), PlanStep.Break(5), PlanStep.Focus(35), PlanStep.Break(15)),
            repeat = true
        )

        assertThat(planSummaryText(plan)).isEqualTo("25 · 5 · 35 · 15 ↻")
    }

    @Test
    fun `an ending plan ends with the check glyph`() {
        assertThat(planSummaryText(SessionPlan.DEFAULT)).isEqualTo("25 · 5 ✓")
    }
}
