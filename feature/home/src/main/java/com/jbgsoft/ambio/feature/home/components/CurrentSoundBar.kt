package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.feature.home.R

@Composable
fun CurrentSoundBar(
    activeMix: List<ActiveSound>,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The repository guarantees a non-empty mix, but the first composition
            // happens before its flow emits.
            if (activeMix.isNotEmpty()) {
                Icon(
                    imageVector = activeMix.first().sound.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Resolved through the inline map, not inside joinToString's
                // non-inline transform, which cannot call a @Composable.
                val names = activeMix.map { stringResource(it.sound.nameRes) }
                Text(
                    // Same label the media notification shows.
                    text = if (names.size <= 2) {
                        names.joinToString(" + ")
                    } else {
                        stringResource(R.string.mix_sound_count, names.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                Text(
                    text = stringResource(R.string.sound_none_selected),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalButton(onClick = onChangeClick) {
            Text(stringResource(R.string.action_change_sound))
        }
    }
}
