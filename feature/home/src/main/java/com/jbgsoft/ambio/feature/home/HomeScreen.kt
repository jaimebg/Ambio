package com.jbgsoft.ambio.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.SoundGlow
import com.jbgsoft.ambio.core.domain.model.TimerPreset
import com.jbgsoft.ambio.core.domain.model.TimerState
import com.jbgsoft.ambio.core.domain.model.gradientOf
import com.jbgsoft.ambio.feature.home.components.CurrentSoundBar
import com.jbgsoft.ambio.feature.home.components.ModeToggle
import com.jbgsoft.ambio.feature.home.components.PlayPauseButton
import com.jbgsoft.ambio.feature.home.components.SoundBottomSheet
import com.jbgsoft.ambio.feature.home.components.SoundPickerContent
import com.jbgsoft.ambio.feature.home.components.TimerDisplay
import com.jbgsoft.ambio.feature.home.components.TimerPresetSelector
import com.jbgsoft.ambio.feature.home.components.VolumeSlider
import com.jbgsoft.ambio.ui.effects.AmbientEffectsOverlay
import com.jbgsoft.ambio.ui.effects.ParticleSource
import com.jbgsoft.ambio.ui.effects.animatedMixGradient
import com.jbgsoft.ambio.ui.effects.mixGradientBackground
import com.jbgsoft.ambio.ui.effects.rememberAmbientEffectsAllowed
import com.jbgsoft.ambio.ui.layout.CONTENT_MAX_WIDTH
import com.jbgsoft.ambio.ui.layout.PaneLayout
import com.jbgsoft.ambio.ui.layout.paneLayoutFor

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToStats = onNavigateToStats
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // The gradient is computed here rather than in the ViewModel for the
        // same reason particleMix below is: it is a pure function of the mix,
        // and remember keyed on the mix recomputes it exactly when the mix
        // changes and never on any other recomposition.
        //
        // ifEmpty guards the very first frame only. HomeUiState starts with an
        // empty activeMix and the repository's real mix arrives a beat later;
        // it is never empty after that, which is why gradientOf requires it.
        val gradient = remember(uiState.activeMix) {
            gradientOf(
                uiState.activeMix.map { it.sound.glow }.ifEmpty { listOf(SoundGlow.RAIN) }
            )
        }

        // Read in the draw phase, not here: animatedMixGradient hands back a
        // State and mixGradientBackground takes a lambda, so the 400 ms
        // cross-fade repaints without re-running this content lambda every frame.
        val animatedGradient = animatedMixGradient(gradient)

        // The gradient and the particles belong to the whole window, not to one
        // pane: on wide screens they run behind the timer and the picker alike.
        // Keep them on this Box, outside the split below.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .mixGradientBackground { animatedGradient.value }
        ) {
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
                val paneLayout = paneLayoutFor(maxWidth)
                val isExpanded = paneLayout == PaneLayout.EXPANDED

                if (isExpanded) {
                    // Wide enough for both at once: the mix sits permanently beside
                    // the timer instead of sliding over it.
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            HomeContentColumn(
                                uiState = uiState,
                                onEvent = onEvent,
                                onNavigateToSettings = onNavigateToSettings,
                                onNavigateToStats = onNavigateToStats,
                                // The picker is already on screen beside this column,
                                // so there is nothing left for the button to open.
                                showChangeButton = false,
                                modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH)
                            )
                        }
                        // Deliberately not wrapped in a verticalScroll: the picker's
                        // grid scrolls itself, and nesting it inside a scrolling
                        // parent would measure it with an unbounded height.
                        SoundPickerContent(
                            sounds = uiState.availableSounds,
                            activeMix = uiState.activeMix,
                            onToggleSound = { onEvent(HomeEvent.ToggleSound(it)) },
                            onLevelChange = { id, level -> onEvent(HomeEvent.SetSoundLevel(id, level)) },
                            onLevelChangeFinished = { id -> onEvent(HomeEvent.SoundLevelChangeFinished(id)) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            columns = 2
                        )
                    }
                } else {
                    HomeContentColumn(
                        uiState = uiState,
                        onEvent = onEvent,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToStats = onNavigateToStats,
                        showChangeButton = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Sound Picker Bottom Sheet
                //
                // Called from in here because this is where the width is known, and
                // the sheet must never coexist with the always-visible picker pane.
                // A ModalBottomSheet draws in its own window, so moving its call
                // site inside this box does not change how it appears.
                if (paneLayout == PaneLayout.COMPACT) {
                    SoundBottomSheet(
                        showSheet = uiState.showSoundPicker,
                        sounds = uiState.availableSounds,
                        activeMix = uiState.activeMix,
                        onToggleSound = { onEvent(HomeEvent.ToggleSound(it)) },
                        onLevelChange = { id, level -> onEvent(HomeEvent.SetSoundLevel(id, level)) },
                        onLevelChangeFinished = { id -> onEvent(HomeEvent.SoundLevelChangeFinished(id)) },
                        onDismiss = { onEvent(HomeEvent.HideSoundPicker) }
                    )
                }
            }
        }
    }
}

/**
 * The timer half of Home: mode toggle, timer, transport controls and the bar
 * naming the current mix.
 *
 * It measures its own box rather than the window because on wide screens it only
 * receives about half of it, and every size below is derived from the space this
 * column actually got.
 */
@Composable
private fun HomeContentColumn(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    showChangeButton: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
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

        // Responsive button sizes. Now that the transport is pinned it no longer
        // has to shout to be found, and every dp it gives back is one the dial
        // and the presets above it get to keep.
        val playButtonSize = if (isSmallScreen) 60.dp else 76.dp
        val resetButtonSize = if (isSmallScreen) 40.dp else 48.dp

        val scrollState = rememberScrollState()

        // Play is the primary action, so it is pinned below the scrolling area
        // rather than left at the end of it: at full size the stack is about
        // 820dp tall, which overflows both a phone in portrait and the half of a
        // landscape tablet the left pane gets, and the button was the part that
        // fell off.
        //
        // Not on the shortest windows, though. A landscape phone has around
        // 410dp of height, and the header, toggle, transport and mix bar leave
        // some 60dp for the dial, so pinning there only trades an invisible
        // button for an invisible timer. Those windows keep scrolling for both,
        // which is what they did before, until they get a layout of their own.
        val pinTransport = !isVerySmallScreen

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable content, with an edge fade marking the cut. The column
            // ends wherever the window runs out, which on a short phone lands
            // mid-element and reads as the end of the screen rather than the top
            // of a scroll.
            val moreBelow by animateFloatAsState(
                targetValue = if (scrollState.canScrollForward) 1f else 0f,
                animationSpec = tween(200),
                label = "scrollMore"
            )

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Outside verticalScroll on purpose: it has to dissolve the
                        // clipped result, not the content before it is cut.
                        .fadingBottomEdge(moreBelow)
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
                        onModeSelected = { onEvent(HomeEvent.SetMode(it)) },
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
                                onPresetSelected = { onEvent(HomeEvent.SelectPreset(it)) },
                                onCustomMinutesChanged = { onEvent(HomeEvent.SetCustomMinutes(it)) },
                                onCustomMinutesChangeFinished = { onEvent(HomeEvent.CustomMinutesChangeFinished) },
                                onBreakMinutesChanged = { onEvent(HomeEvent.SetBreakMinutes(it)) },
                                onBreakMinutesChangeFinished = { onEvent(HomeEvent.BreakMinutesChangeFinished) },
                                modifier = Modifier.fillMaxWidth(),
                                isCompact = isSmallScreen
                            )
                        }
                    }

                    if (!pinTransport) {
                        Spacer(modifier = Modifier.height(sectionSpacing))
                        TransportControls(
                            uiState = uiState,
                            onEvent = onEvent,
                            isSmallScreen = isSmallScreen,
                            playButtonSize = playButtonSize,
                            resetButtonSize = resetButtonSize,
                            controlsSpacing = controlsSpacing
                        )
                    }
                }

                ScrollMoreChevron(
                    amount = moreBelow,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (pinTransport) {
                Spacer(modifier = Modifier.height(controlsSpacing))
                TransportControls(
                    uiState = uiState,
                    onEvent = onEvent,
                    isSmallScreen = isSmallScreen,
                    playButtonSize = playButtonSize,
                    resetButtonSize = resetButtonSize,
                    controlsSpacing = controlsSpacing
                )
            }

            Spacer(modifier = Modifier.height(controlsSpacing))
            CurrentSoundBar(
                activeMix = uiState.activeMix,
                onChangeClick = { onEvent(HomeEvent.ShowSoundPicker) },
                modifier = Modifier.fillMaxWidth(),
                showChangeButton = showChangeButton
            )
        }
    }
}

/** How tall the fade marking more scrollable content is. */
private val SCROLL_FADE_HEIGHT = 44.dp

/**
 * Dissolves the bottom of a scrolling area so the cut reads as "more below"
 * rather than as the end of the screen.
 *
 * It erases the content's own alpha with [BlendMode.DstIn] instead of painting a
 * scrim over it. A scrim has to fade towards some colour, and this screen's
 * background is an animated mix gradient, so black left a visible band with a
 * hard edge where it met the transport below. Dissolving reveals whatever the
 * gradient happens to be at that moment, so there is no edge to see.
 *
 * The offscreen layer is only paid for while the fade is actually drawn: the
 * particle field already has a frame budget worth protecting.
 */
private fun Modifier.fadingBottomEdge(amount: Float): Modifier =
    if (amount <= 0f) {
        this
    } else {
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = SCROLL_FADE_HEIGHT.toPx() * amount
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fade,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    }

/** The chevron that says the dissolve above it is scrollable, not decorative. */
@Composable
private fun ScrollMoreChevron(amount: Float, modifier: Modifier = Modifier) {
    if (amount <= 0f) return

    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        // Decoration: everything it points at is reachable by scrolling and is
        // already announced on its own.
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f * amount),
        modifier = modifier.size(20.dp)
    )
}

/** Play/pause, reset and volume: the controls that drive playback. */
@Composable
private fun TransportControls(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    isSmallScreen: Boolean,
    playButtonSize: Dp,
    resetButtonSize: Dp,
    controlsSpacing: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
                        onClick = { onEvent(HomeEvent.Reset) },
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
                onClick = { onEvent(HomeEvent.PlayPause) },
                size = playButtonSize
            )
        }

        // Volume Slider
        VolumeSlider(
            volume = uiState.volume,
            onVolumeChange = { onEvent(HomeEvent.SetVolume(it)) },
            onVolumeChangeFinished = { onEvent(HomeEvent.VolumeChangeFinished) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
