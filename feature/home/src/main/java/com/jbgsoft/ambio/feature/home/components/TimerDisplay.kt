package com.jbgsoft.ambio.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.TimerState

private const val MODE_TRANSITION_DURATION = 300

@Composable
fun TimerDisplay(
    timerState: TimerState,
    mode: AppMode,
    isPlaying: Boolean,
    selectedMinutes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp
) {
    val progress = when (timerState) {
        is TimerState.Running -> timerState.progress
        is TimerState.Paused -> timerState.remainingMs.toFloat() / timerState.totalMs.toFloat()
        is TimerState.Idle -> 1f
        is TimerState.Completed -> 0f
    }

    val isAnimating = timerState is TimerState.Running && isPlaying

    val displayText = when {
        mode == AppMode.AMBIENT -> "∞"
        timerState is TimerState.Running -> formatTime(timerState.remainingMs)
        timerState is TimerState.Paused -> formatTime(timerState.remainingMs)
        timerState is TimerState.Completed -> "00:00"
        else -> formatTime(selectedMinutes * 60 * 1000L)
    }

    val subtitleText = when {
        mode == AppMode.AMBIENT -> "Ambient Mode"
        timerState is TimerState.Running && timerState.isBreak -> "Break Time"
        timerState is TimerState.Running -> "Focus"
        timerState is TimerState.Paused -> "Paused"
        timerState is TimerState.Completed && timerState.wasBreak -> "Break Over!"
        timerState is TimerState.Completed -> "Completed!"
        else -> "Ready"
    }

    // CircularProgress is 20dp smaller than container to leave room for glow effect
    val circularProgressSize = size - 20.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        CircularProgress(
            progress = progress,
            isAnimating = isAnimating,
            size = circularProgressSize,
            modifier = Modifier.size(circularProgressSize)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animate display text when switching modes
            AnimatedContent(
                targetState = mode to displayText,
                transitionSpec = {
                    if (initialState.first != targetState.first) {
                        // Mode change: scale + fade animation
                        (fadeIn(tween(MODE_TRANSITION_DURATION)) +
                            scaleIn(tween(MODE_TRANSITION_DURATION), initialScale = 0.8f))
                            .togetherWith(
                                fadeOut(tween(MODE_TRANSITION_DURATION)) +
                                    scaleOut(tween(MODE_TRANSITION_DURATION), targetScale = 0.8f)
                            )
                    } else {
                        // Timer tick: no animation (instant update)
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(0)),
                            initialContentExit = fadeOut(tween(0))
                        )
                    }
                },
                label = "displayText"
            ) { (_, text) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Animate subtitle text when changing
            AnimatedContent(
                targetState = subtitleText,
                transitionSpec = {
                    fadeIn(tween(MODE_TRANSITION_DURATION))
                        .togetherWith(fadeOut(tween(MODE_TRANSITION_DURATION)))
                },
                label = "subtitleText"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
