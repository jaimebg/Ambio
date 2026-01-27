package com.jbgsoft.ambio.core.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.TimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TimerRepositoryImpl.
 *
 * Tests verify the timer's state transitions, countdown behavior, and pause/resume functionality.
 * Uses Turbine for Flow testing and kotlinx-coroutines-test for controlling virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerRepositoryImplTest {

    private lateinit var timerRepository: TimerRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        timerRepository = TimerRepositoryImpl(testDispatcher)
    }

    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startTimer transitions to Running state`() = runTest(testDispatcher) {
        val duration = 5000L // 5 seconds

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            timerRepository.startTimer(duration)
            advanceTimeBy(100) // Allow coroutine to start

            val runningState = awaitItem()
            assertThat(runningState).isInstanceOf(TimerState.Running::class.java)

            val running = runningState as TimerState.Running
            assertThat(running.remainingMs).isEqualTo(duration)
            assertThat(running.totalMs).isEqualTo(duration)
            assertThat(running.isBreak).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timer counts down every second`() = runTest(testDispatcher) {
        val duration = 3000L // 3 seconds

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            timerRepository.startTimer(duration)
            advanceTimeBy(100) // Start the timer

            // Initial running state
            val initial = awaitItem() as TimerState.Running
            assertThat(initial.remainingMs).isEqualTo(3000L)

            // After 1 second
            advanceTimeBy(1000)
            val afterOne = awaitItem() as TimerState.Running
            assertThat(afterOne.remainingMs).isEqualTo(2000L)

            // After 2 seconds
            advanceTimeBy(1000)
            val afterTwo = awaitItem() as TimerState.Running
            assertThat(afterTwo.remainingMs).isEqualTo(1000L)

            // After 3 seconds - timer completes
            advanceTimeBy(1000)
            val afterThree = awaitItem() as TimerState.Running
            assertThat(afterThree.remainingMs).isEqualTo(0L)

            // Completed state (wasBreak = false for regular timer)
            assertThat(awaitItem()).isEqualTo(TimerState.Completed(wasBreak = false))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pauseTimer transitions from Running to Paused`() = runTest(testDispatcher) {
        val duration = 10000L // 10 seconds

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            timerRepository.startTimer(duration)
            advanceTimeBy(100)

            val running = awaitItem() as TimerState.Running
            assertThat(running.remainingMs).isEqualTo(duration)

            // Advance 3 seconds
            advanceTimeBy(3000)
            skipItems(3) // Skip the 3 intermediate Running states

            // Pause the timer
            timerRepository.pauseTimer()

            val paused = awaitItem()
            assertThat(paused).isInstanceOf(TimerState.Paused::class.java)

            val pausedState = paused as TimerState.Paused
            assertThat(pausedState.remainingMs).isEqualTo(7000L) // 10 - 3 seconds
            assertThat(pausedState.totalMs).isEqualTo(duration)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resumeTimer continues from paused state`() = runTest(testDispatcher) {
        val duration = 5000L

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            // Start and pause
            timerRepository.startTimer(duration)
            advanceTimeBy(100)
            awaitItem() // Running

            advanceTimeBy(2000)
            skipItems(2) // Skip 2 tick updates

            timerRepository.pauseTimer()
            val paused = awaitItem() as TimerState.Paused
            assertThat(paused.remainingMs).isEqualTo(3000L)

            // Resume
            timerRepository.resumeTimer()
            advanceTimeBy(100)

            val resumed = awaitItem() as TimerState.Running
            assertThat(resumed.remainingMs).isEqualTo(3000L)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetTimer returns to Idle state`() = runTest(testDispatcher) {
        val duration = 10000L

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            // Start timer
            timerRepository.startTimer(duration)
            advanceTimeBy(100)
            awaitItem() // Running

            // Reset
            timerRepository.resetTimer()

            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startBreak sets isBreak flag to true`() = runTest(testDispatcher) {
        val breakDuration = 5000L

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            timerRepository.startBreak(breakDuration)
            advanceTimeBy(100)

            val breakState = awaitItem() as TimerState.Running
            assertThat(breakState.isBreak).isTrue()
            assertThat(breakState.remainingMs).isEqualTo(breakDuration)
            assertThat(breakState.totalMs).isEqualTo(breakDuration)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `break completion sets wasBreak flag to true`() = runTest(testDispatcher) {
        val breakDuration = 2000L // 2 seconds

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            timerRepository.startBreak(breakDuration)
            advanceTimeBy(100)

            val initial = awaitItem() as TimerState.Running
            assertThat(initial.isBreak).isTrue()

            // After 1 second
            advanceTimeBy(1000)
            val afterOne = awaitItem() as TimerState.Running
            assertThat(afterOne.remainingMs).isEqualTo(1000L)

            // After 2 seconds - break completes
            advanceTimeBy(1000)
            val afterTwo = awaitItem() as TimerState.Running
            assertThat(afterTwo.remainingMs).isEqualTo(0L)

            // Completed state with wasBreak = true
            assertThat(awaitItem()).isEqualTo(TimerState.Completed(wasBreak = true))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `progress is calculated correctly`() = runTest(testDispatcher) {
        val duration = 10000L // 10 seconds

        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            timerRepository.startTimer(duration)
            advanceTimeBy(100)

            val initial = awaitItem() as TimerState.Running
            assertThat(initial.progress).isEqualTo(1.0f)

            // After 5 seconds (50%)
            advanceTimeBy(5000)
            skipItems(4)
            val halfway = awaitItem() as TimerState.Running
            assertThat(halfway.progress).isEqualTo(0.5f)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `starting new timer cancels previous timer`() = runTest(testDispatcher) {
        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            // Start first timer
            timerRepository.startTimer(10000L)
            advanceTimeBy(100)
            awaitItem() // Running

            advanceTimeBy(2000)
            skipItems(2)

            // Start new timer - should reset
            timerRepository.startTimer(5000L)
            advanceTimeBy(100)

            val newTimer = awaitItem() as TimerState.Running
            assertThat(newTimer.remainingMs).isEqualTo(5000L)
            assertThat(newTimer.totalMs).isEqualTo(5000L)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pauseTimer does nothing when not Running`() = runTest(testDispatcher) {
        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            // Pause when Idle - should do nothing
            timerRepository.pauseTimer()

            // No state change expected - use expectNoEvents
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resumeTimer does nothing when not Paused`() = runTest(testDispatcher) {
        timerRepository.timerState.test {
            assertThat(awaitItem()).isEqualTo(TimerState.Idle)

            // Resume when Idle - should do nothing
            timerRepository.resumeTimer()

            // No state change expected
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
