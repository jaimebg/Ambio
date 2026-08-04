package com.jbgsoft.ambio.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
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
class SettingsViewModelTest {

    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state reflects the stored preferences`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(
            UserPreferences(hapticsEnabled = false, chimeEnabled = true, effectsEnabled = false)
        )

        val viewModel = SettingsViewModel(preferencesRepository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.hapticsEnabled).isFalse()
            assertThat(state.chimeEnabled).isTrue()
            assertThat(state.effectsEnabled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling haptics writes through to the repository`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(UserPreferences())

        val viewModel = SettingsViewModel(preferencesRepository)
        advanceUntilIdle()

        viewModel.onHapticsChanged(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { preferencesRepository.setHapticsEnabled(false) }
    }
}
