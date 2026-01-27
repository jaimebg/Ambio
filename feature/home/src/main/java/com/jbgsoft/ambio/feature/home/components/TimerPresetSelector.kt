package com.jbgsoft.ambio.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.TimerPreset

@Composable
fun TimerPresetSelector(
    selectedPreset: TimerPreset,
    customMinutes: Int,
    onPresetSelected: (TimerPreset) -> Unit,
    onCustomMinutesChanged: (Int) -> Unit,
    onCustomMinutesChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val horizontalPadding = if (isCompact) 12.dp else 16.dp
    val customSectionHorizontalPadding = if (isCompact) 16.dp else 32.dp
    val customSectionVerticalPadding = if (isCompact) 8.dp else 16.dp
    val spacerHeight = if (isCompact) 4.dp else 8.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
            modifier = Modifier.padding(horizontal = horizontalPadding)
        ) {
            TimerPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onPresetSelected(preset) },
                    label = { Text(preset.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = selectedPreset == TimerPreset.CUSTOM,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = customSectionHorizontalPadding,
                        vertical = customSectionVerticalPadding
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$customMinutes min",
                    style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(spacerHeight))
                Slider(
                    value = customMinutes.toFloat(),
                    onValueChange = { onCustomMinutesChanged(it.toInt()) },
                    onValueChangeFinished = onCustomMinutesChangeFinished,
                    valueRange = 1f..120f,
                    steps = 118, // 120 - 1 - 1 = 118 steps for whole numbers
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1 min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "120 min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
