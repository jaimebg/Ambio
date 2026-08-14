package com.jbgsoft.ambio.feature.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.Session
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.repository.SessionRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.usecase.DeleteSessionUseCase
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

    private val rainSound = Sound(
        id = "rain",
        nameRes = 101,
        icon = Icons.Default.WaterDrop,
        audioRes = 1,
        theme = SoundTheme.RAIN
    )

    private val fireplaceSound = Sound(
        id = "fireplace",
        nameRes = 102,
        icon = Icons.Default.WaterDrop,
        audioRes = 3,
        theme = SoundTheme.FIREPLACE
    )

    // "wind" is the real pre-rename id of what is now "cave"; it is deliberately
    // never stubbed to a Sound, so getSoundById("wind") resolves to null.
    private fun session(soundId: String) = Session(
        id = 1L,
        soundId = soundId,
        durationMinutes = 25,
        completedAt = 1_700_000_000_000L,
        wasCompleted = true
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel(sessions: List<Session> = listOf(session("wind"))): StatsViewModel {
        every { sessionRepository.getAllSessions() } returns flowOf(sessions)
        every { sessionRepository.getTotalFocusMinutes() } returns flowOf(25)
        every { sessionRepository.getCompletedSessionCount() } returns flowOf(1)
        return StatsViewModel(
            getSessionStats = GetSessionStatsUseCase(sessionRepository),
            getSessionHistory = GetSessionHistoryUseCase(sessionRepository),
            deleteSession = DeleteSessionUseCase(sessionRepository),
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
            assertThat(state.sessions.first().soundNameResIds).hasSize(1)
            assertThat(state.sessions.first().soundNameResIds.single()).isNull()
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

    @Test
    fun `a session recorded before the mixer shows its single sound`() = runTest {
        every { soundRepository.getSoundById("rain") } returns rainSound

        val viewModel = createViewModel(sessions = listOf(session("rain")))
        advanceUntilIdle()

        viewModel.uiState.test {
            val row = awaitItem().sessions.single()
            assertThat(row.soundNameResIds).hasSize(1)
            assertThat(row.soundNameResIds.single()).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session recorded with a mix shows every sound`() = runTest {
        every { soundRepository.getSoundById("rain") } returns rainSound
        every { soundRepository.getSoundById("fireplace") } returns fireplaceSound

        val viewModel = createViewModel(sessions = listOf(session("rain,fireplace")))
        advanceUntilIdle()

        viewModel.uiState.test {
            val row = awaitItem().sessions.single()
            assertThat(row.soundNameResIds).hasSize(2)
            assertThat(row.soundNameResIds.none { it == null }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown id inside a mix does not drop the sounds beside it`() = runTest {
        // "wind" is the real pre-rename id of what is now "cave".
        every { soundRepository.getSoundById("rain") } returns rainSound
        every { soundRepository.getSoundById("wind") } returns null

        val viewModel = createViewModel(sessions = listOf(session("rain,wind")))
        advanceUntilIdle()

        viewModel.uiState.test {
            val row = awaitItem().sessions.single()
            assertThat(row.soundNameResIds).hasSize(2)
            assertThat(row.soundNameResIds[0]).isNotNull()
            assertThat(row.soundNameResIds[1]).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
