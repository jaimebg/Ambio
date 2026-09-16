package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.feature.home.R

/** The durations offered, in minutes. 0 is "off" and is always the first chip. */
private val SLEEP_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

/**
 * Ambient mode's sleep timer: one chip per duration, where Timer mode shows the
 * focus presets. Bedtime is the use, so the choice is made once and persisted,
 * and pressing play starts the countdown (see HomeViewModel).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSelector(
    sleepMinutes: Int,
    onSleepMinutesSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val horizontalPadding = if (isCompact) 16.dp else 24.dp
    val labelSpacing = if (isCompact) 6.dp else 8.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.label_sleep_timer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(labelSpacing))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SLEEP_OPTIONS.forEach { minutes ->
                val isSelected = sleepMinutes == minutes
                FilterChip(
                    selected = isSelected,
                    onClick = { onSleepMinutesSelected(minutes) },
                    label = {
                        Text(
                            if (minutes == 0) stringResource(R.string.sleep_timer_off)
                            else pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
                        )
                    },
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
