package com.jbgsoft.ambio.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.TimerPreset

private val BREAK_OPTIONS = listOf(5, 10, 15, 20)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerPresetSelector(
    selectedPreset: TimerPreset,
    customMinutes: Int,
    breakMinutes: Int,
    onPresetSelected: (TimerPreset) -> Unit,
    onCustomMinutesChanged: (Int) -> Unit,
    onCustomMinutesChangeFinished: () -> Unit,
    onBreakMinutesChanged: (Int) -> Unit,
    onBreakMinutesChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val horizontalPadding = if (isCompact) 16.dp else 24.dp
    val sectionSpacing = if (isCompact) 16.dp else 20.dp
    val labelSpacing = if (isCompact) 6.dp else 8.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Focus Duration - Segmented Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Focus Duration",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(labelSpacing))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                TimerPreset.entries.forEachIndexed { index, preset ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TimerPreset.entries.size
                        ),
                        onClick = { onPresetSelected(preset) },
                        selected = selectedPreset == preset,
                        label = { Text(preset.displayName) }
                    )
                }
            }
        }

        // Custom Focus Time Stepper
        AnimatedVisibility(
            visible = selectedPreset == TimerPreset.CUSTOM,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = sectionSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberStepper(
                    value = customMinutes,
                    onValueChange = onCustomMinutesChanged,
                    onValueChangeFinished = onCustomMinutesChangeFinished,
                    minValue = 1,
                    maxValue = 120,
                    step = 5,
                    suffix = "min",
                    isCompact = isCompact
                )
            }
        }

        Spacer(modifier = Modifier.height(sectionSpacing))

        // Break Duration - Filter Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Break Duration",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(labelSpacing))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BREAK_OPTIONS.forEach { minutes ->
                    val isSelected = breakMinutes == minutes
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onBreakMinutesChanged(minutes)
                            onBreakMinutesChangeFinished()
                        },
                        label = { Text("$minutes min") },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    minValue: Int,
    maxValue: Int,
    step: Int,
    suffix: String,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (isCompact) 40.dp else 48.dp
    val iconSize = if (isCompact) 20.dp else 24.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Decrease button
        IconButton(
            onClick = {
                val newValue = (value - step).coerceAtLeast(minValue)
                onValueChange(newValue)
                onValueChangeFinished()
            },
            enabled = value > minValue,
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(modifier = Modifier.width(if (isCompact) 16.dp else 24.dp))

        // Value display
        Text(
            text = "$value $suffix",
            style = if (isCompact) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.width(if (isCompact) 16.dp else 24.dp))

        // Increase button
        IconButton(
            onClick = {
                val newValue = (value + step).coerceAtMost(maxValue)
                onValueChange(newValue)
                onValueChangeFinished()
            },
            enabled = value < maxValue,
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
