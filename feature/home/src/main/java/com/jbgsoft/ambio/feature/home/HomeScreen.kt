package com.jbgsoft.ambio.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.SoundTheme
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.feature.home.components.CurrentSoundBar
import com.jbgsoft.ambio.feature.home.components.ModeToggle
import com.jbgsoft.ambio.feature.home.components.PlayPauseButton
import com.jbgsoft.ambio.feature.home.components.SoundBottomSheet
import com.jbgsoft.ambio.feature.home.components.TimerDisplay
import com.jbgsoft.ambio.feature.home.components.TimerPresetSelector
import com.jbgsoft.ambio.feature.home.components.VolumeSlider
import com.jbgsoft.ambio.ui.theme.AmbioTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dynamic theming: entire UI recolors based on selected sound with smooth 400ms transition
    AmbioTheme(soundTheme = uiState.selectedSound?.theme ?: SoundTheme.RAIN) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section - Mode Toggle
            ModeToggle(
                selectedMode = uiState.mode,
                onModeSelected = { viewModel.onEvent(HomeEvent.SetMode(it)) },
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            // Center Section - Timer Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
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
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Timer Presets (only in Timer mode)
                AnimatedVisibility(
                    visible = uiState.mode == AppMode.TIMER,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TimerPresetSelector(
                        selectedPreset = uiState.selectedPreset,
                        customMinutes = uiState.customMinutes,
                        onPresetSelected = { viewModel.onEvent(HomeEvent.SelectPreset(it)) },
                        onCustomMinutesChanged = { viewModel.onEvent(HomeEvent.SetCustomMinutes(it)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bottom Section - Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Play/Pause Button
                PlayPauseButton(
                    isPlaying = uiState.isPlaying,
                    onClick = { viewModel.onEvent(HomeEvent.PlayPause) }
                )

                // Volume Slider
                VolumeSlider(
                    volume = uiState.volume,
                    onVolumeChange = { viewModel.onEvent(HomeEvent.SetVolume(it)) },
                    onVolumeChangeFinished = { /* Volume is saved on each change */ },
                    modifier = Modifier.fillMaxWidth()
                )

                // Current Sound Bar
                CurrentSoundBar(
                    sound = uiState.selectedSound,
                    onChangeClick = { viewModel.onEvent(HomeEvent.ShowSoundPicker) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

            // Sound Picker Bottom Sheet
            SoundBottomSheet(
                showSheet = uiState.showSoundPicker,
                sounds = uiState.availableSounds,
                selectedSound = uiState.selectedSound,
                onSoundSelected = { viewModel.onEvent(HomeEvent.SelectSound(it)) },
                onDismiss = { viewModel.onEvent(HomeEvent.HideSoundPicker) }
            )
        }
    }
}
