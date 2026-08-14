package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.feature.home.R

/**
 * Sized for a three-column grid. The catalogue is thirteen sounds, so at the
 * previous two columns and 120dp only four tiles were on screen at once and
 * building a mix meant scrolling past most of the options.
 *
 * The level slider stays inside the active tile, which at three columns leaves
 * it roughly 86dp of track. That is a real cost -- it is coarse for fine
 * adjustment -- and is the accepted trade for seeing nine tiles instead of four.
 */
private val IDLE_HEIGHT = 96.dp
private val ACTIVE_HEIGHT = 132.dp
private val ICON_SIZE = 28.dp
private val CARD_PADDING = 10.dp

@Composable
fun SoundCard(
    sound: Sound,
    isActive: Boolean,
    level: Float,
    canDeactivate: Boolean,
    canActivate: Boolean,
    onToggle: () -> Unit,
    onLevelChange: (Float) -> Unit,
    onLevelChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toggleLabel = stringResource(
        when {
            isActive -> R.string.mix_remove_sound
            canActivate -> R.string.mix_add_sound
            else -> R.string.mix_limit_reached
        },
        stringResource(sound.nameRes)
    )
    val levelLabel = stringResource(R.string.mix_level_for, stringResource(sound.nameRes))

    Card(
        onClick = onToggle,
        enabled = if (isActive) canDeactivate else canActivate,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isActive) ACTIVE_HEIGHT else IDLE_HEIGHT)
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
                .padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // contentDescription lives on the card, not here: the card is the whole
            // touch target, and TalkBack should not announce the name twice.
            Icon(
                imageVector = sound.icon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(sound.nameRes),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                // Two lines so "Brown Noise" and "Fireplace" wrap rather than
                // truncate in a ~106dp column; ellipsis only as a last resort at
                // large font scales.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isActive) {
                // onValueChangeFinished is what keeps the drag off the disk: every
                // frame moves the level, only the lift persists it. Same contract as
                // VolumeSlider.
                Slider(
                    value = level,
                    onValueChange = onLevelChange,
                    onValueChangeFinished = onLevelChangeFinished,
                    modifier = Modifier.semantics { contentDescription = levelLabel }
                )
            }
        }
    }
}

