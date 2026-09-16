package com.jbgsoft.ambio.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.common.audio.ChimePlayer
import com.jbgsoft.ambio.core.common.haptics.HapticManager
import com.jbgsoft.ambio.core.common.resources.StringProvider
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.model.UserPreferences
import com.jbgsoft.ambio.core.domain.repository.ChimeRepository
import com.jbgsoft.ambio.core.domain.repository.PreferencesRepository
import com.jbgsoft.ambio.core.domain.repository.SoundRepository
import com.jbgsoft.ambio.core.domain.repository.TimerRepository
import com.jbgsoft.ambio.core.domain.session.SessionEvent
import com.jbgsoft.ambio.core.domain.session.SessionProgress
import com.jbgsoft.ambio.core.domain.session.SessionRunner
import com.jbgsoft.ambio.core.domain.usecase.SaveSessionUseCase
import com.jbgsoft.ambio.media.AudioServiceConnection
import com.jbgsoft.ambio.media.MixEntry
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for HomeViewModel.
 *
 * Tests verify the ViewModel's state management, event handling, and integration
 * with repositories and services. Uses MockK for dependency mocking and Turbine
 * for Flow testing.
 *
 * These tests are critical for ensuring:
 * - Timer mode correctly starts/pauses/resumes the timer and audio
 * - Ambient mode toggles audio playback without timer
 * - Toggling sounds builds a mix and pushes each change to the audio service
 * - Volume changes are applied immediately and persisted
 * - Timer completion triggers chime, haptic feedback, and session saving
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Mocks
    private lateinit var soundRepository: SoundRepository
    private lateinit var timerRepository: TimerRepository
    private lateinit var sessionRunner: SessionRunner
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var saveSessionUseCase: SaveSessionUseCase
    private lateinit var hapticManager: HapticManager
    private lateinit var audioServiceConnection: AudioServiceConnection
    private lateinit var chimePlayer: ChimePlayer
    private lateinit var chimeRepository: ChimeRepository

    private val stringProvider = object : StringProvider {
        override fun get(id: Int, vararg args: Any): String = "test-string-$id"
        override fun getQuantity(id: Int, quantity: Int, vararg args: Any): String = "test-string-$id"
    }

    // Flows for controlling state
    private lateinit var timerStateFlow: MutableStateFlow<TimerState>
    private lateinit var activeMixFlow: MutableStateFlow<List<ActiveSound>>
    private lateinit var preferencesFlow: MutableStateFlow<UserPreferences>
    private lateinit var isConnectedFlow: MutableStateFlow<Boolean>
    private lateinit var isPlayingFlow: MutableStateFlow<Boolean>
    private lateinit var progressFlow: MutableStateFlow<SessionProgress?>
    private lateinit var eventsFlow: MutableSharedFlow<SessionEvent>

    // Test data
    private val testSound = Sound(
        id = "rain",
        nameRes = 5,
        icon = Icons.Default.WaterDrop,
        audioRes = 1,
        theme = SoundTheme.RAIN,
        glow = SoundGlow.RAIN
    )

    private val testSoundForest = Sound(
        id = "forest",
        nameRes = 6,
        icon = Icons.Default.WaterDrop,
        audioRes = 3,
        theme = SoundTheme.FOREST,
        glow = SoundGlow.FOREST
    )

    private val testSounds = listOf(testSound, testSoundForest)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Initialize flows
        timerStateFlow = MutableStateFlow(TimerState.Idle)
        activeMixFlow = MutableStateFlow(listOf(ActiveSound(testSound, 1.0f)))
        preferencesFlow = MutableStateFlow(UserPreferences())
        isConnectedFlow = MutableStateFlow(false)
        isPlayingFlow = MutableStateFlow(false)

        // Create mocks
        soundRepository = mockk {
            every { getAllSounds() } returns testSounds
            every { getActiveMix() } returns activeMixFlow
            coEvery { setSoundActive(any(), any()) } just Runs
            coEvery { setSoundLevel(any(), any()) } just Runs
        }

        timerRepository = mockk {
            every { timerState } returns timerStateFlow
            coEvery { startTimer(any()) } just Runs
            coEvery { pauseTimer() } just Runs
            coEvery { resumeTimer() } just Runs
            coEvery { resetTimer() } just Runs
            coEvery { startBreak(any()) } just Runs
        }

        progressFlow = MutableStateFlow(null)
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 16)
        sessionRunner = mockk {
            every { progress } returns progressFlow
            every { events } returns eventsFlow
            coEvery { start(any()) } just Runs
            coEvery { pause() } just Runs
            coEvery { resume() } just Runs
            coEvery { stop() } just Runs
        }

        preferencesRepository = mockk {
            every { preferences } returns preferencesFlow
            coEvery { setLastMix(any()) } just Runs
            coEvery { setVolume(any()) } just Runs
            coEvery { setLastTimerMinutes(any()) } just Runs
            coEvery { setBreakMinutes(any()) } just Runs
            coEvery { setSessionPlan(any()) } just Runs
            coEvery { setLastMode(any()) } just Runs
        }

        saveSessionUseCase = mockk()
        coEvery {
            saveSessionUseCase(
                soundId = any(),
                durationMinutes = any(),
                wasCompleted = any()
            )
        } just Runs

        hapticManager = mockk {
            every { click() } just Runs
            every { tick() } just Runs
            every { heavyClick() } just Runs
            every { timerComplete() } just Runs
        }

        audioServiceConnection = mockk {
            every { connect() } just Runs
            every { disconnect() } just Runs
            every { isConnected } returns isConnectedFlow
            every { isPlaying } returns isPlayingFlow
            every { setMix(any(), any()) } just Runs
            every { play() } just Runs
            every { pause() } just Runs
            every { stop() } just Runs
            every { setVolume(any()) } just Runs
        }

        chimePlayer = mockk {
            every { playChime(any()) } just Runs
        }

        chimeRepository = mockk {
            every { getTimerChimeResource() } returns 100 // Dummy resource ID
            every { getSuccessChimeResource() } returns 200
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            soundRepository = soundRepository,
            timerRepository = timerRepository,
            sessionRunner = sessionRunner,
            preferencesRepository = preferencesRepository,
            saveSessionUseCase = saveSessionUseCase,
            hapticManager = hapticManager,
            audioServiceConnection = audioServiceConnection,
            chimePlayer = chimePlayer,
            chimeRepository = chimeRepository,
            stringProvider = stringProvider
        )
    }

    // --- Initialization Tests ---

    @Test
    fun `initial state has default values`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.mode).isEqualTo(AppMode.TIMER)
            assertThat(state.selectedPreset).isEqualTo(TimerPreset.FOCUS_25)
            assertThat(state.timerState).isEqualTo(TimerState.Idle)
            assertThat(state.showSoundPicker).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads available sounds on init`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.availableSounds).isEqualTo(testSounds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads the active mix on init`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.activeMix).isEqualTo(listOf(ActiveSound(testSound, 1.0f)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connects to audio service on init`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        verify { audioServiceConnection.connect() }
    }

    @Test
    fun `disconnects from audio service when ViewModel is cleared`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Call protected onCleared() using reflection to verify cleanup behavior
        val onClearedMethod = viewModel.javaClass.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        verify { audioServiceConnection.disconnect() }
    }

    // --- Mode Toggle Tests ---

    @Test
    fun `setMode updates mode in state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.mode).isEqualTo(AppMode.AMBIENT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMode triggers haptic feedback`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        verify { hapticManager.click() }
    }

    @Test
    fun `setMode persists mode preference`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        coVerify { preferencesRepository.setLastMode(AppMode.AMBIENT) }
    }

    // --- Sound Selection Tests ---

    @Test
    fun `toggleSound triggers haptic feedback`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        verify { hapticManager.heavyClick() }
    }

    @Test
    fun `toggleSound no longer persists directly to preferences`() = runTest(testDispatcher) {
        // Persistence now happens inside SoundRepositoryImpl.setSoundActive, not here —
        // this guards against the call creeping back and double-writing the store.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        coVerify(exactly = 0) { preferencesRepository.setLastMix(any()) }
    }

    @Test
    fun `toggleSound leaves the sound picker open`() = runTest(testDispatcher) {
        // Building a mix takes several taps, so the sheet stays until dismissed.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ShowSoundPicker)
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showSoundPicker).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Timer Preset Tests ---

    @Test
    fun `selectPreset updates preset in state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.FOCUS_50))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedPreset).isEqualTo(TimerPreset.FOCUS_50)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectPreset triggers haptic feedback`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.FOCUS_50))
        advanceUntilIdle()

        verify { hapticManager.click() }
    }

    @Test
    fun `selectPreset saves minutes for non-custom presets`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.FOCUS_50))
        advanceUntilIdle()

        coVerify { preferencesRepository.setLastTimerMinutes(50) }
    }

    @Test
    fun `selectPreset does not save minutes for custom preset`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.CUSTOM))
        advanceUntilIdle()

        coVerify(exactly = 0) { preferencesRepository.setLastTimerMinutes(any()) }
    }

    // --- Custom Minutes Tests ---

    @Test
    fun `setCustomMinutes updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetCustomMinutes(45))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.customMinutes).isEqualTo(45)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCustomMinutes clamps to 1-120 range`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Test minimum clamping
        viewModel.onEvent(HomeEvent.SetCustomMinutes(-5))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.customMinutes).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCustomMinutes clamps to maximum 120`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetCustomMinutes(200))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.customMinutes).isEqualTo(120)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customMinutesChangeFinished triggers tick haptic`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetCustomMinutes(45))
        viewModel.onEvent(HomeEvent.CustomMinutesChangeFinished)
        advanceUntilIdle()

        verify { hapticManager.tick() }
    }

    // --- Volume Tests ---

    @Test
    fun `setVolume updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetVolume(0.5f))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.volume).isEqualTo(0.5f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setVolume clamps to 0-1 range`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetVolume(1.5f))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.volume).isEqualTo(1.0f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setVolume applies to audio service immediately`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetVolume(0.5f))
        advanceUntilIdle()

        verify { audioServiceConnection.setVolume(0.5f) }
    }

    @Test
    fun `volumeChangeFinished persists to preferences`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetVolume(0.5f))
        viewModel.onEvent(HomeEvent.VolumeChangeFinished)
        advanceUntilIdle()

        coVerify { preferencesRepository.setVolume(0.5f) }
    }

    // --- Play/Pause Tests (Timer Mode) ---

    @Test
    fun `playPause in timer mode starts the preset's two-step plan when idle`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        coVerify { sessionRunner.start(SessionPlan.quick(25, 5)) }
        coVerify(exactly = 0) { timerRepository.startTimer(any()) }
    }

    @Test
    fun `the 50 minute preset starts a 50 and break plan`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.FOCUS_50))
        viewModel.onEvent(HomeEvent.SetBreakMinutes(10))

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        coVerify { sessionRunner.start(SessionPlan.quick(50, 10)) }
    }

    @Test
    fun `the custom preset uses the custom minutes`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.CUSTOM))
        viewModel.onEvent(HomeEvent.SetCustomMinutes(40))

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        coVerify { sessionRunner.start(SessionPlan.quick(40, 5)) }
    }

    @Test
    fun `the plan preset starts the stored plan`() = runTest(testDispatcher) {
        val plan = SessionPlan(listOf(PlanStep.Focus(35), PlanStep.Break(15)), repeat = true)
        preferencesFlow.value = UserPreferences(sessionPlan = plan)
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEvent(HomeEvent.SelectPreset(TimerPreset.PLAN))

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        coVerify { sessionRunner.start(plan) }
    }

    @Test
    fun `a started step plays the mix`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.StepStarted(PlanStep.Focus(25), 0))
        advanceUntilIdle()

        verifyOrder {
            audioServiceConnection.setMix(any(), any())
            audioServiceConnection.play()
        }
    }

    @Test
    fun `playPause in timer mode pauses the runner and the audio when running`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        timerStateFlow.value = TimerState.Running(remainingMs = 60_000, totalMs = 60_000)
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        coVerify { sessionRunner.pause() }
        verify { audioServiceConnection.pause() }
    }

    @Test
    fun `playPause in timer mode resumes the runner and the audio when paused`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        timerStateFlow.value = TimerState.Paused(remainingMs = 30_000, totalMs = 60_000)
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        coVerify { sessionRunner.resume() }
        verify { audioServiceConnection.play() }
    }

    @Test
    fun `playPause triggers heavy click haptic`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        verify { hapticManager.heavyClick() }
    }

    // --- Play/Pause Tests (Ambient Mode) ---

    @Test
    fun `playPause in ambient mode plays audio when not playing`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        verify { audioServiceConnection.play() }
    }

    @Test
    fun `playPause in ambient mode pauses audio when playing`() = runTest(testDispatcher) {
        isPlayingFlow.value = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()

        verify { audioServiceConnection.pause() }
    }

    // --- Reset Tests ---

    @Test
    fun `reset stops the runner and the audio`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.Reset)
        advanceUntilIdle()

        coVerify { sessionRunner.stop() }
        verify { audioServiceConnection.stop() }
    }

    @Test
    fun `reset triggers click haptic`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.Reset)
        advanceUntilIdle()

        verify { hapticManager.click() }
    }

    @Test
    fun `switching to ambient mode stops a running plan`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        progressFlow.value = SessionProgress(SessionPlan.DEFAULT, 0, 0)
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        coVerify { sessionRunner.stop() }
    }

    // --- Sound Picker Tests ---

    @Test
    fun `showSoundPicker updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ShowSoundPicker)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showSoundPicker).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideSoundPicker updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ShowSoundPicker)
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.HideSoundPicker)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showSoundPicker).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showSoundPicker triggers click haptic`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ShowSoundPicker)
        advanceUntilIdle()

        verify { hapticManager.click() }
    }

    // --- Session Event Tests ---

    @Test
    fun `a completed focus step stops audio, chimes, buzzes and saves the session`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.StepCompleted(PlanStep.Focus(25), 0))
        advanceUntilIdle()

        verify { audioServiceConnection.stop() }
        verify { chimePlayer.playChime(100) }
        verify { hapticManager.timerComplete() }
        coVerify { saveSessionUseCase(soundId = "rain", durationMinutes = 25, wasCompleted = true) }
    }

    @Test
    fun `a completed break step chimes but saves nothing`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.StepCompleted(PlanStep.Break(5), 1))
        advanceUntilIdle()

        verify { chimePlayer.playChime(100) }
        coVerify(exactly = 0) { saveSessionUseCase(any(), any(), any()) }
    }

    @Test
    fun `a completed plan plays the success chime instead of the step chime`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.PlanCompleted(PlanStep.Break(5)))
        advanceUntilIdle()

        verify { audioServiceConnection.stop() }
        verify { chimePlayer.playChime(200) }
        verify(exactly = 0) { chimePlayer.playChime(100) }
        verify { hapticManager.timerComplete() }
    }

    @Test
    fun `a plan that ends on a focus step still saves that session`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.PlanCompleted(PlanStep.Focus(35)))
        advanceUntilIdle()

        coVerify { saveSessionUseCase(soundId = "rain", durationMinutes = 35, wasCompleted = true) }
    }

    @Test
    fun `no chime plays when the chime is disabled`() = runTest(testDispatcher) {
        preferencesFlow.value = UserPreferences(chimeEnabled = false)
        createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.StepCompleted(PlanStep.Focus(25), 0))
        eventsFlow.emit(SessionEvent.PlanCompleted(PlanStep.Break(5)))
        advanceUntilIdle()

        verify(exactly = 0) { chimePlayer.playChime(any()) }
    }

    @Test
    fun `session progress lands in state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val progress = SessionProgress(SessionPlan.DEFAULT, 1, 0)

        progressFlow.value = progress
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sessionProgress).isEqualTo(progress)
    }

    // --- Audio Service Connection State Tests ---

    @Test
    fun `service connection state updates UI`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        isConnectedFlow.value = true
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isServiceConnected).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `playback state updates UI`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        isPlayingFlow.value = true
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isPlaying).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Preferences Observation Tests ---

    @Test
    fun `volume preference updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        preferencesFlow.value = UserPreferences(volume = 0.3f)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.volume).isEqualTo(0.3f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mode preference updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        preferencesFlow.value = UserPreferences(lastMode = AppMode.AMBIENT)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.mode).isEqualTo(AppMode.AMBIENT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `custom timer minutes from preferences updates state only for non-standard values`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 45 is not 25 or 50, so it should update customMinutes
        preferencesFlow.value = UserPreferences(lastTimerMinutes = 45)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.customMinutes).isEqualTo(45)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Timer State Observation Tests ---

    @Test
    fun `timer running state updates UI`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val runningState = TimerState.Running(
            remainingMs = 1200000L,
            totalMs = 1500000L
        )
        timerStateFlow.value = runningState
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.timerState).isEqualTo(runningState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timer paused state updates UI`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val pausedState = TimerState.Paused(
            remainingMs = 500000L,
            totalMs = 1500000L
        )
        timerStateFlow.value = pausedState
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.timerState).isEqualTo(pausedState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Haptics Preference Gate Tests ---

    @Test
    fun `no haptic feedback fires when haptics are disabled`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(
            UserPreferences(hapticsEnabled = false)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        verify(exactly = 0) { hapticManager.heavyClick() }
        verify(exactly = 0) { hapticManager.click() }
    }

    @Test
    fun `haptic feedback fires when haptics are enabled`() = runTest {
        every { preferencesRepository.preferences } returns flowOf(
            UserPreferences(hapticsEnabled = true)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.PlayPause)
        viewModel.onEvent(HomeEvent.SetMode(AppMode.AMBIENT))
        advanceUntilIdle()

        verify(atLeast = 1) { hapticManager.heavyClick() }
        verify(atLeast = 1) { hapticManager.click() }
    }

    // --- Mixer Tests ---

    @Test
    fun `toggling a sound on tells only the repository`() = runTest {
        val viewModel = createViewModel()
        // Without this the mix flow has not emitted yet and every toggle below would
        // read an empty mix — three of these tests would then pass vacuously.
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        coVerify { soundRepository.setSoundActive("forest", true) }
        // The service hears about it through getActiveMix, never from the event: two
        // writers would be two sources of truth that can disagree.
        verify(exactly = 0) { audioServiceConnection.setMix(any(), any()) }
    }

    @Test
    fun `toggling an active sound off tells only the repository`() = runTest {
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 1f))
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        viewModel.onEvent(HomeEvent.ToggleSound(testSoundForest))
        advanceUntilIdle()

        coVerify { soundRepository.setSoundActive("forest", false) }
        verify(exactly = 0) { audioServiceConnection.setMix(any(), any()) }
    }

    @Test
    fun `a rejected toggle reaches neither the repository nor the service`() = runTest {
        // Exactly one sound, or the assertions below prove nothing.
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f))
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        viewModel.onEvent(HomeEvent.ToggleSound(testSound))
        advanceUntilIdle()

        coVerify(exactly = 0) { soundRepository.setSoundActive("rain", false) }
        verify(exactly = 0) { audioServiceConnection.setMix(any(), any()) }
    }

    @Test
    fun `a rejected toggle does not buzz`() = runTest {
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.ToggleSound(testSound))
        advanceUntilIdle()

        verify(exactly = 0) { hapticManager.heavyClick() }
    }

    // --- Per-sound level tests ---
    //
    // A level slider is a drag: it fires on every frame. It therefore follows the master
    // volume slider's contract — state and audio move now, the store is written when the
    // finger lifts — rather than the toggle's, which goes to the repository and waits.

    @Test
    fun `dragging a level reaches the service without touching the store`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        viewModel.onEvent(HomeEvent.SetSoundLevel("rain", 0.3f))
        advanceUntilIdle()

        // Live audio, because a level the user cannot hear until they let go is
        // unusable for balancing a mix.
        verify { audioServiceConnection.setMix(listOf(MixEntry("rain", 1, 0.3f)), any()) }
        // But no write: the repository's setSoundLevel is a DataStore edit behind a
        // mutex, and a drag would queue one per frame.
        coVerify(exactly = 0) { soundRepository.setSoundLevel(any(), any()) }
    }

    @Test
    fun `the level is in state before the event handler returns`() = runTest {
        // No advanceUntilIdle: if this needed one, the thumb would be waiting on a round
        // trip through the repository and would lag behind the finger.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetSoundLevel("rain", 0.3f))

        assertThat(viewModel.uiState.value.activeMix.single().level).isEqualTo(0.3f)
    }

    @Test
    fun `a level is clamped into zero to one`() = runTest {
        // Starts below 1f so an unclamped value would be visible in both places.
        activeMixFlow.value = listOf(ActiveSound(testSound, 0.5f))
        val viewModel = createViewModel()
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        viewModel.onEvent(HomeEvent.SetSoundLevel("rain", 4f))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeMix.single().level).isEqualTo(1f)
        verify { audioServiceConnection.setMix(listOf(MixEntry("rain", 1, 1f)), any()) }
    }

    @Test
    fun `dragging a level changes neither the membership nor another sound's level`() =
        runTest {
            // The palette is a function of which sounds are active and of nothing else,
            // so a slider must not be able to move it.
            activeMixFlow.value =
                listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 0.8f))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(HomeEvent.SetSoundLevel("forest", 0.2f))
            advanceUntilIdle()

            val mix = viewModel.uiState.value.activeMix
            assertThat(mix.map { it.sound.id }).containsExactly("rain", "forest").inOrder()
            assertThat(mix.single { it.sound.id == "rain" }.level).isEqualTo(1f)
        }

    @Test
    fun `letting go of a level slider persists the level the drag ended on`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.SetSoundLevel("rain", 0.3f))
        viewModel.onEvent(HomeEvent.SetSoundLevel("rain", 0.6f))
        viewModel.onEvent(HomeEvent.SoundLevelChangeFinished("rain"))
        advanceUntilIdle()

        coVerify(exactly = 1) { soundRepository.setSoundLevel("rain", 0.6f) }
    }

    @Test
    fun `every mix emission pushes the whole mix to the service`() = runTest {
        createViewModel()
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        activeMixFlow.value = listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 0.4f))
        advanceUntilIdle()

        verify {
            audioServiceConnection.setMix(
                listOf(MixEntry("rain", 1, 1f), MixEntry("forest", 3, 0.4f)),
                any()
            )
        }
    }

    @Test
    fun `connecting pushes the whole mix to the service`() = runTest {
        // The service keeps no state across a disconnect: if this push is skipped the
        // app looks fine until the service restarts, then plays nothing.
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 0.4f))
        createViewModel()
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        isConnectedFlow.value = true
        advanceUntilIdle()

        verify {
            audioServiceConnection.setMix(
                listOf(MixEntry("rain", 1, 1f), MixEntry("forest", 3, 0.4f)),
                any()
            )
        }
    }

    @Test
    fun `connecting before the mix resolves still ends up pushing it`() = runTest {
        // getActiveMix is a combine over DataStore and the controller future resolves
        // independently, so either can win. Whichever loses must still push.
        activeMixFlow.value = emptyList()
        createViewModel()
        isConnectedFlow.value = true
        advanceUntilIdle()

        verify(exactly = 0) { audioServiceConnection.setMix(any(), any()) }

        activeMixFlow.value = listOf(ActiveSound(testSound, 1f))
        advanceUntilIdle()

        verify { audioServiceConnection.setMix(listOf(MixEntry("rain", 1, 1f)), any()) }
    }

    @Test
    fun `stop then play re-declares the mix before playing`() = runTest {
        // stop() releases every track service-side. Without the re-declaration the
        // user presses play after a reset and hears silence.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(HomeEvent.Reset)
        advanceUntilIdle()
        clearMocks(audioServiceConnection, answers = false)

        viewModel.onEvent(HomeEvent.PlayPause)
        advanceUntilIdle()
        // The runner, not playPause, starts the audio now: the step it reports is
        // what re-declares the mix.
        eventsFlow.emit(SessionEvent.StepStarted(PlanStep.Focus(25), 0))
        advanceUntilIdle()

        verifyOrder {
            audioServiceConnection.setMix(listOf(MixEntry("rain", 1, 1f)), any())
            audioServiceConnection.play()
        }
    }

    @Test
    fun `a completed session records every sound in the mix`() = runTest {
        activeMixFlow.value = listOf(ActiveSound(testSound, 1f), ActiveSound(testSoundForest, 0.5f))
        val viewModel = createViewModel()
        advanceUntilIdle()

        eventsFlow.emit(SessionEvent.StepCompleted(PlanStep.Focus(25), 0))
        advanceUntilIdle()

        coVerify {
            saveSessionUseCase(
                soundId = "rain,forest",
                durationMinutes = any(),
                wasCompleted = true
            )
        }
    }
}
