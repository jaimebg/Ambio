package com.jbgsoft.ambio.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanCodecTest {

    private val six = SessionPlan(
        listOf(
            PlanStep.Focus(25), PlanStep.Break(5),
            PlanStep.Focus(25), PlanStep.Break(5),
            PlanStep.Focus(35), PlanStep.Break(15)
        ),
        repeat = true
    )

    @Test
    fun `a looping plan encodes as letters, minutes and a loop suffix`() {
        assertThat(PlanCodec.encode(six)).isEqualTo("F25,B5,F25,B5,F35,B15;loop")
    }

    @Test
    fun `an ending plan carries an end suffix`() {
        assertThat(PlanCodec.encode(SessionPlan.DEFAULT)).isEqualTo("F25,B5;end")
    }

    @Test
    fun `encode then decode is the identity`() {
        assertThat(PlanCodec.decode(PlanCodec.encode(six))).isEqualTo(six)
        assertThat(PlanCodec.decode(PlanCodec.encode(SessionPlan.DEFAULT))).isEqualTo(SessionPlan.DEFAULT)
    }

    @Test
    fun `an empty string decodes to the default plan`() {
        assertThat(PlanCodec.decode("")).isEqualTo(SessionPlan.DEFAULT)
    }

    @Test
    fun `garbage decodes to the default plan`() {
        assertThat(PlanCodec.decode("hello;world")).isEqualTo(SessionPlan.DEFAULT)
    }

    @Test
    fun `a missing suffix reads as end`() {
        assertThat(PlanCodec.decode("F10,B2")).isEqualTo(
            SessionPlan(listOf(PlanStep.Focus(10), PlanStep.Break(2)), repeat = false)
        )
    }

    @Test
    fun `unreadable segments are skipped, not fatal`() {
        assertThat(PlanCodec.decode("F10,,x9,B2;loop")).isEqualTo(
            SessionPlan(listOf(PlanStep.Focus(10), PlanStep.Break(2)), repeat = true)
        )
    }

    @Test
    fun `minutes out of range are clamped rather than rejected`() {
        assertThat(PlanCodec.decode("F999,B0;end")).isEqualTo(
            SessionPlan(listOf(PlanStep.Focus(120), PlanStep.Break(1)), repeat = false)
        )
    }

    @Test
    fun `more than twelve steps are truncated to twelve`() {
        val encoded = List(13) { "F1" }.joinToString(",") + ";end"

        assertThat(PlanCodec.decode(encoded).steps).hasSize(12)
    }

    @Test
    fun `a plan with only breaks decodes to the default plan`() {
        assertThat(PlanCodec.decode("B5,B5;loop")).isEqualTo(SessionPlan.DEFAULT)
    }

    @Test
    fun `letters are read case-insensitively`() {
        assertThat(PlanCodec.decode("f10,b2;LOOP")).isEqualTo(
            SessionPlan(listOf(PlanStep.Focus(10), PlanStep.Break(2)), repeat = true)
        )
    }
}
