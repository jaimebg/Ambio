package com.jbgsoft.ambio.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for TimerState sealed class.
 *
 * Tests verify the progress calculation and state properties.
 */
class TimerStateTest {

    @Test
    fun `Running progress is 1_0 when full time remaining`() {
        val state = TimerState.Running(
            remainingMs = 25000L,
            totalMs = 25000L
        )
        assertThat(state.progress).isEqualTo(1.0f)
    }

    @Test
    fun `Running progress is 0_5 at half time`() {
        val state = TimerState.Running(
            remainingMs = 12500L,
            totalMs = 25000L
        )
        assertThat(state.progress).isEqualTo(0.5f)
    }

    @Test
    fun `Running progress is 0_0 when no time remaining`() {
        val state = TimerState.Running(
            remainingMs = 0L,
            totalMs = 25000L
        )
        assertThat(state.progress).isEqualTo(0.0f)
    }

    @Test
    fun `Running progress is 0_25 at quarter time`() {
        val state = TimerState.Running(
            remainingMs = 6250L,
            totalMs = 25000L
        )
        assertThat(state.progress).isEqualTo(0.25f)
    }

    @Test
    fun `Running isBreak defaults to false`() {
        val state = TimerState.Running(
            remainingMs = 5000L,
            totalMs = 10000L
        )
        assertThat(state.isBreak).isFalse()
    }

    @Test
    fun `Running can be created with isBreak true`() {
        val state = TimerState.Running(
            remainingMs = 5000L,
            totalMs = 10000L,
            isBreak = true
        )
        assertThat(state.isBreak).isTrue()
    }

    @Test
    fun `Paused state stores remaining and total time`() {
        val state = TimerState.Paused(
            remainingMs = 12345L,
            totalMs = 50000L
        )
        assertThat(state.remainingMs).isEqualTo(12345L)
        assertThat(state.totalMs).isEqualTo(50000L)
    }

    @Test
    fun `Idle is a singleton`() {
        val idle1 = TimerState.Idle
        val idle2 = TimerState.Idle
        assertThat(idle1).isSameInstanceAs(idle2)
    }

    @Test
    fun `Completed is a singleton`() {
        val completed1 = TimerState.Completed
        val completed2 = TimerState.Completed
        assertThat(completed1).isSameInstanceAs(completed2)
    }

    @Test
    fun `Running with same values are equal`() {
        val state1 = TimerState.Running(5000L, 10000L, false)
        val state2 = TimerState.Running(5000L, 10000L, false)
        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `Paused with same values are equal`() {
        val state1 = TimerState.Paused(5000L, 10000L)
        val state2 = TimerState.Paused(5000L, 10000L)
        assertThat(state1).isEqualTo(state2)
    }
}
