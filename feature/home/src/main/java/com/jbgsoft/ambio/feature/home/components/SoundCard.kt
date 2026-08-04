package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.feature.home.R

@Composable
fun SoundCard(
    sound: Sound,
    isActive: Boolean,
    level: Float,
    canDeactivate: Boolean,
    onToggle: () -> Unit,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val toggleLabel = stringResource(
        if (isActive) R.string.mix_remove_sound else R.string.mix_add_sound,
        stringResource(sound.nameRes)
    )
    val levelLabel = stringResource(R.string.mix_level_for, stringResource(sound.nameRes))

    Card(
        onClick = onToggle,
        enabled = !isActive || canDeactivate,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isActive) 160.dp else 120.dp)
            .semantics { contentDescription = toggleLabel },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // contentDescription lives on the card, not here: the card is the whole
            // touch target, and TalkBack should not announce the name twice.
            Icon(
                imageVector = sound.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(sound.nameRes),
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            if (isActive) {
                Slider(
                    value = level,
                    onValueChange = onLevelChange,
                    modifier = Modifier.semantics { contentDescription = levelLabel }
                )
            }
        }
    }
}

@Composable
fun ComingSoonCard(
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { },
        enabled = false,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .alpha(0.5f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sound_more_coming),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
