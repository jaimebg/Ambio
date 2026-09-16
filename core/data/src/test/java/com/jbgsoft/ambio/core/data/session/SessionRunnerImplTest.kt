package com.jbgsoft.ambio.core.data.session

import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.data.repository.TimerRepositoryImpl
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.session.SessionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Drives a real TimerRepositoryImpl on virtual time. Never advanceUntilIdle():
 * a looping plan ticks forever, so time is advanced by exact amounts and the
 * runner's own collector is flushed with runCurrent().
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRunnerImplTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var timer: TimerRepositoryImpl
    private lateinit var runner: SessionRunnerImpl

    private val oneMinute = 60_000L

    private val threeSteps = SessionPlan(
        listOf(PlanStep.Focus(1), PlanStep.Break(1), PlanStep.Focus(2)),
        repeat = false
    )

    @Before
    fun setUp() {
        timer = TimerRepositoryImpl(dispatcher)
        runner = SessionRunnerImpl(timer, dispatcher)
    }

    /** Collects events into a list for the rest of the test. */
    private fun kotlinx.coroutines.test.TestScope.recordEvents(): MutableList<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(dispatcher) { runner.events.collect { events += it } }
        runCurrent()
        return events
    }

    private fun kotlinx.coroutines.test.TestScope.finishStep(minutes: Int) {
        advanceTimeBy(minutes * oneMinute + 1)
        runCurrent()
    }

    @Test
    fun `start puts the first step on the timer and reports it`() = runTest(dispatcher) {
        val events = recordEvents()

        runner.start(threeSteps)
        runCurrent()

        assertThat(runner.progress.value?.stepIndex).isEqualTo(0)
        assertThat(runner.progress.value?.lap).isEqualTo(0)
        val running = timer.timerState.first() as TimerState.Running
        assertThat(running.totalMs).isEqualTo(oneMinute)
        assertThat(running.isBreak).isFalse()
        assertThat(events).containsExactly(SessionEvent.StepStarted(PlanStep.Focus(1), 0))
    }

    @Test
    fun `a finished step is reported and the next one starts as a break`() = runTest(dispatcher) {
        val events = recordEvents()
        runner.start(threeSteps)
        runCurrent()

        finishStep(1)

        assertThat(runner.progress.value?.stepIndex).isEqualTo(1)
        val running = timer.timerState.first() as TimerState.Running
        assertThat(running.isBreak).isTrue()
        assertThat(events).containsExactly(
            SessionEvent.StepStarted(PlanStep.Focus(1), 0),
            SessionEvent.StepCompleted(PlanStep.Focus(1), 0),
            SessionEvent.StepStarted(PlanStep.Break(1), 1)
        ).inOrder()
    }

    @Test
    fun `the last step of an ending plan reports only PlanCompleted and clears progress`() = runTest(dispatcher) {
        val events = recordEvents()
        runner.start(threeSteps)
        runCurrent()

        finishStep(1)
        finishStep(1)
        finishStep(2)

        assertThat(runner.progress.value).isNull()
        assertThat(timer.timerState.first()).isEqualTo(TimerState.Idle)
        assertThat(events.last()).isEqualTo(SessionEvent.PlanCompleted(PlanStep.Focus(2)))
        assertThat(events.filterIsInstance<SessionEvent.StepCompleted>().map { it.stepIndex })
            .containsExactly(0, 1).inOrder()
    }

    @Test
    fun `a looping plan wraps to the first step and counts the lap`() = runTest(dispatcher) {
        val events = recordEvents()
        val looping = threeSteps.copy(repeat = true)
        runner.start(looping)
        runCurrent()

        finishStep(1)
        finishStep(1)
        finishStep(2)

        assertThat(runner.progress.value?.stepIndex).isEqualTo(0)
        assertThat(runner.progress.value?.lap).isEqualTo(1)
        assertThat(events).contains(SessionEvent.StepCompleted(PlanStep.Focus(2), 2))
        assertThat(events.filterIsInstance<SessionEvent.PlanCompleted>()).isEmpty()
        assertThat(events.last()).isEqualTo(SessionEvent.StepStarted(PlanStep.Focus(1), 0))

        // A repeat=true plan never reaches Completed on its own, so the wrapped
        // step is still actively ticking here. runTest's cleanup drains the
        // shared TestCoroutineScheduler to true idle (kotlinx.coroutines.test's
        // runTest -> finally { testScheduler.advanceUntilIdleOr { false } }), which
        // never happens while this timer keeps rescheduling itself - stop it first,
        // exactly like the other tests' cancelAndIgnoreRemainingEvents() equivalent.
        runner.stop()
        runCurrent()
    }

    @Test
    fun `pause and resume act on the current step`() = runTest(dispatcher) {
        runner.start(threeSteps)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        runner.pause()
        runCurrent()
        assertThat(timer.timerState.first()).isInstanceOf(TimerState.Paused::class.java)
        assertThat(runner.progress.value?.stepIndex).isEqualTo(0)

        runner.resume()
        runCurrent()
        val running = timer.timerState.first() as TimerState.Running
        assertThat(running.remainingMs).isEqualTo(oneMinute - 10_000)
    }

    @Test
    fun `stop mid-step clears progress, resets the timer and reports nothing`() = runTest(dispatcher) {
        val events = recordEvents()
        runner.start(threeSteps)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        runner.stop()
        runCurrent()

        assertThat(runner.progress.value).isNull()
        assertThat(timer.timerState.first()).isEqualTo(TimerState.Idle)
        assertThat(events).containsExactly(SessionEvent.StepStarted(PlanStep.Focus(1), 0))
    }

    @Test
    fun `start while a plan runs replaces it from its first step`() = runTest(dispatcher) {
        runner.start(threeSteps)
        runCurrent()
        finishStep(1)

        runner.start(SessionPlan.quick(50, 10))
        runCurrent()

        assertThat(runner.progress.value?.plan).isEqualTo(SessionPlan.quick(50, 10))
        assertThat(runner.progress.value?.stepIndex).isEqualTo(0)
        assertThat((timer.timerState.first() as TimerState.Running).totalMs).isEqualTo(50 * oneMinute)
    }

    @Test
    fun `progress exposes the focus ordinal and count`() = runTest(dispatcher) {
        runner.start(threeSteps)
        runCurrent()
        finishStep(1)
        finishStep(1)

        val progress = runner.progress.value!!
        assertThat(progress.step).isEqualTo(PlanStep.Focus(2))
        assertThat(progress.focusOrdinal).isEqualTo(2)
        assertThat(progress.focusCount).isEqualTo(2)
    }

    @Test
    fun `start rejects an invalid plan`() = runTest(dispatcher) {
        val invalid = SessionPlan(listOf(PlanStep.Break(5)), repeat = false)

        val result = runCatching { runner.start(invalid) }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runner.progress.value).isNull()
    }
}
