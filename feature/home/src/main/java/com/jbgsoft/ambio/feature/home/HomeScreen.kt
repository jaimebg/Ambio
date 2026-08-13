package com.jbgsoft.ambio.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.feature.home.components.CurrentSoundBar
import com.jbgsoft.ambio.feature.home.components.ModeToggle
import com.jbgsoft.ambio.feature.home.components.PlayPauseButton
import com.jbgsoft.ambio.feature.home.components.SoundBottomSheet
import com.jbgsoft.ambio.feature.home.components.TimerDisplay
import com.jbgsoft.ambio.feature.home.components.TimerPresetSelector
import com.jbgsoft.ambio.feature.home.components.VolumeSlider
import com.jbgsoft.ambio.ui.effects.AmbientEffectsOverlay
import com.jbgsoft.ambio.ui.effects.ParticleSource
import com.jbgsoft.ambio.ui.effects.rememberAmbientEffectsAllowed

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient effects BEHIND content
            val effectsAllowed = rememberAmbientEffectsAllowed()
            if (uiState.effectsEnabled && effectsAllowed) {
                val particleMix = remember(uiState.activeMix) {
                    uiState.activeMix.map { ParticleSource(it.sound.theme, it.level) }
                }
                AmbientEffectsOverlay(
                    isPlaying = uiState.isPlaying,
                    mix = particleMix
                )
            }

            // Main UI content ON TOP
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                val screenHeight = maxHeight
                val screenWidth = maxWidth

                // Responsive sizing based on available height
                // Small screen threshold: ~600dp (typical small phone in portrait)
                val isSmallScreen = screenHeight < 600.dp
                val isVerySmallScreen = screenHeight < 500.dp

                // Responsive timer display size
                val timerDisplaySize: Dp = when {
                    isVerySmallScreen -> min(200f, screenWidth.value * 0.55f).dp
                    isSmallScreen -> min(240f, screenWidth.value * 0.6f).dp
                    else -> min(300f, screenWidth.value * 0.75f).dp
                }

                // Responsive spacing
                val verticalPadding = if (isSmallScreen) 12.dp else 24.dp
                val sectionSpacing = if (isSmallScreen) 12.dp else 24.dp
                val controlsSpacing = if (isSmallScreen) 16.dp else 24.dp

                // Responsive button sizes
                val playButtonSize = if (isSmallScreen) 72.dp else 96.dp
                val resetButtonSize = if (isSmallScreen) 44.dp else 56.dp

                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onNavigateToStats) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = stringResource(R.string.action_open_stats)
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.action_open_settings)
                                )
                            }
                        }

                        // Top Section - Mode Toggle
                        ModeToggle(
                            selectedMode = uiState.mode,
                            onModeSelected = { viewModel.onEvent(HomeEvent.SetMode(it)) },
                            modifier = Modifier.padding(horizontal = if (isSmallScreen) 16.dp else 32.dp)
                        )

                        Spacer(modifier = Modifier.height(sectionSpacing))

                        // Center Section - Timer Display
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            TimerDisplay(
                                timerState = uiState.timerState,
                                mode = uiState.mode,
                                isPlaying = uiState.isPlaying,
                                selectedMinutes = when (uiState.selectedPreset) {
                                    TimerPreset.FOCUS_25 -> 25
                                    TimerPreset.FOCUS_50 -> 50
                                    TimerPreset.CUSTOM -> uiState.customMinutes
                                },
                                size = timerDisplaySize
                            )

                            Spacer(modifier = Modifier.height(sectionSpacing))

                            // Timer Presets (only in Timer mode)
                            AnimatedVisibility(
                                visible = uiState.mode == AppMode.TIMER,
                                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                            ) {
                                TimerPresetSelector(
                                    selectedPreset = uiState.selectedPreset,
                                    customMinutes = uiState.customMinutes,
                                    breakMinutes = uiState.breakMinutes,
                                    onPresetSelected = { viewModel.onEvent(HomeEvent.SelectPreset(it)) },
                                    onCustomMinutesChanged = { viewModel.onEvent(HomeEvent.SetCustomMinutes(it)) },
                                    onCustomMinutesChangeFinished = { viewModel.onEvent(HomeEvent.CustomMinutesChangeFinished) },
                                    onBreakMinutesChanged = { viewModel.onEvent(HomeEvent.SetBreakMinutes(it)) },
                                    onBreakMinutesChangeFinished = { viewModel.onEvent(HomeEvent.BreakMinutesChangeFinished) },
                                    modifier = Modifier.fillMaxWidth(),
                                    isCompact = isSmallScreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(sectionSpacing))

                        // Controls Section - Play/Pause, Reset, Volume
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(controlsSpacing)
                        ) {
                            // Play/Pause and Reset Buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Reset Button (only visible when timer is active)
                                val showReset = uiState.mode == AppMode.TIMER &&
                                    (uiState.timerState is TimerState.Running || uiState.timerState is TimerState.Paused)

                                AnimatedVisibility(
                                    visible = showReset,
                                    enter = fadeIn(tween(200)),
                                    exit = fadeOut(tween(200))
                                ) {
                                    Row {
                                        FloatingActionButton(
                                            onClick = { viewModel.onEvent(HomeEvent.Reset) },
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            elevation = FloatingActionButtonDefaults.elevation(
                                                defaultElevation = 2.dp
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .size(resetButtonSize)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = stringResource(R.string.action_reset),
                                                modifier = Modifier.size(if (isSmallScreen) 20.dp else 24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(if (isSmallScreen) 12.dp else 16.dp))
                                    }
                                }

                                // Play/Pause Button
                                PlayPauseButton(
                                    isPlaying = uiState.isPlaying,
                                    onClick = { viewModel.onEvent(HomeEvent.PlayPause) },
                                    size = playButtonSize
                                )
                            }

                            // Volume Slider
                            VolumeSlider(
                                volume = uiState.volume,
                                onVolumeChange = { viewModel.onEvent(HomeEvent.SetVolume(it)) },
                                onVolumeChangeFinished = { viewModel.onEvent(HomeEvent.VolumeChangeFinished) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Fixed at bottom - Current Sound Bar (outside scroll)
                    Spacer(modifier = Modifier.height(controlsSpacing))
                    CurrentSoundBar(
                        activeMix = uiState.activeMix,
                        onChangeClick = { viewModel.onEvent(HomeEvent.ShowSoundPicker) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Sound Picker Bottom Sheet
        SoundBottomSheet(
            showSheet = uiState.showSoundPicker,
            sounds = uiState.availableSounds,
            activeMix = uiState.activeMix,
            onToggleSound = { viewModel.onEvent(HomeEvent.ToggleSound(it)) },
            onLevelChange = { id, level -> viewModel.onEvent(HomeEvent.SetSoundLevel(id, level)) },
            onLevelChangeFinished = { id -> viewModel.onEvent(HomeEvent.SoundLevelChangeFinished(id)) },
            onDismiss = { viewModel.onEvent(HomeEvent.HideSoundPicker) }
        )
    }
}
