package com.jbgsoft.ambio.feature.stats

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.usecase.GetSessionHistoryUseCase
import com.jbgsoft.ambio.core.domain.usecase.GetSessionStatsUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val soundRepository: SoundRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val session = Session(
        id = 1L,
        soundId = "wind",
        durationMinutes = 25,
        completedAt = 1_700_000_000_000L,
        wasCompleted = true
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(): StatsViewModel {
        every { sessionRepository.getAllSessions() } returns flowOf(listOf(session))
        every { sessionRepository.getTotalFocusMinutes() } returns flowOf(25)
        every { sessionRepository.getCompletedSessionCount() } returns flowOf(1)
        return StatsViewModel(
            getSessionStats = GetSessionStatsUseCase(sessionRepository),
            getSessionHistory = GetSessionHistoryUseCase(sessionRepository),
            sessionRepository = sessionRepository,
            soundRepository = soundRepository
        )
    }

    @Test
    fun `a session whose sound no longer exists carries a null name resource`() = runTest {
        every { soundRepository.getSoundById("wind") } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.sessions).hasSize(1)
            assertThat(state.sessions.first().soundNameRes).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totals come through from the stats use case`() = runTest {
        every { soundRepository.getSoundById(any()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.totalFocusMinutes).isEqualTo(25)
            assertThat(state.completedSessionCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a session reaches the repository`() = runTest {
        every { soundRepository.getSoundById(any()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onDeleteSession(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { sessionRepository.deleteSession(1L) }
    }
}
