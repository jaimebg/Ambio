package com.jbgsoft.ambio.feature.home.components

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
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.core.domain.model.TimerState

@Composable
fun TimerDisplay(
    timerState: TimerState,
    mode: AppMode,
    isPlaying: Boolean,
    selectedMinutes: Int,
    modifier: Modifier = Modifier
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
        timerState is TimerState.Completed -> "Completed!"
        else -> "Ready"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(300.dp)
    ) {
        CircularProgress(
            progress = progress,
            isAnimating = isAnimating,
            modifier = Modifier.size(280.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
