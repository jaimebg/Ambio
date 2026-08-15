package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.feature.home.R

/**
 * Sized for a three-column grid. The catalogue is twelve sounds, so at the
 * previous two columns and 120dp only four tiles were on screen at once and
 * building a mix meant scrolling past most of the options.
 *
 * The level slider stays inside the active tile, which at three columns leaves
 * it roughly 86dp of track. That is a real cost -- it is coarse for fine
 * adjustment -- and is the accepted trade for seeing nine tiles instead of four.
 *
 * One height for every tile, active or not. Growing by 36dp on activation
 * reflowed the whole grid under the tap and left it ragged, with rows of
 * different heights sitting next to each other. The level slot below is
 * therefore always reserved and only filled when the sound is in the mix, and
 * the bar in it is drawn slim so reserving it costs little.
 */
private val CARD_HEIGHT = 116.dp
private val ICON_SIZE = 28.dp
private val CARD_PADDING = 10.dp

/** Two lines of labelMedium, which is what the name is allowed to take. */
private val LABEL_BLOCK_HEIGHT = 32.dp

/** Reserved for the level bar whether or not one is drawn. */
private val LEVEL_SLOT_HEIGHT = 24.dp
private val LEVEL_TRACK_HEIGHT = 6.dp
private val LEVEL_KNOB_SIZE = 14.dp

// The slider's track and thumb slots are what keep the level bar small enough
// to live in a tile; both are still experimental in Material 3.
@OptIn(ExperimentalMaterial3Api::class)
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

    // The reserved level slot costs the name some room, and at large text that
    // was enough to drop it from two lines to one: at three columns and 1.3x,
    // "Kayumangging ingay" truncated instead of wrapping. Give the tile back
    // exactly what the second line grew by, so default text keeps compact tiles
    // and scaled text keeps whole names.
    val fontScale = LocalDensity.current.fontScale
    val cardHeight = CARD_HEIGHT + (LABEL_BLOCK_HEIGHT * (fontScale - 1f)).coerceAtLeast(0.dp)

    Card(
        onClick = onToggle,
        enabled = if (isActive) canDeactivate else canActivate,
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
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
                .fillMaxSize()
                .padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon and name take whatever the level slot leaves and centre in it,
            // so a one-line name and a two-line one still sit level across a row.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LEVEL_SLOT_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                if (isActive) {
                    // onValueChangeFinished is what keeps the drag off the disk: every
                    // frame moves the level, only the lift persists it. Same contract as
                    // VolumeSlider.
                    //
                    // Default M3 furniture is far too tall for a tile, so the track and
                    // thumb are drawn here instead. The slot stays the slider's whole
                    // height, which keeps the drag target as tall as the reserved space
                    // rather than shrinking it to the 4dp the track paints.
                    Slider(
                        value = level,
                        onValueChange = onLevelChange,
                        onValueChangeFinished = onLevelChangeFinished,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LEVEL_SLOT_HEIGHT)
                            .semantics { contentDescription = levelLabel },
                        // Empty: the knob is drawn inside the track below. Material
                        // places the thumb against the track's measured height, so a
                        // slim track and a separate thumb drift apart vertically --
                        // 2dp at a 4dp track, which is plainly visible on a circle.
                        // Drawing both as children of one centred box makes them
                        // concentric by construction instead.
                        thumb = {},
                        // Material's own track rather than a hand-drawn one: it
                        // shares the thumb's travel range, so the two line up at
                        // both ends. A Box filled to the level does not, and left
                        // the circle hanging off the right while the track sat
                        // inset on the left.
                        track = { state ->
                            val fraction = state.value.coerceIn(0f, 1f)
                            val on = MaterialTheme.colorScheme.primary

                            // Every child is CenterStart, so the rail, the fill and
                            // the knob all share one vertical centre whatever their
                            // heights are.
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(LEVEL_KNOB_SIZE),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val travel = maxWidth - LEVEL_KNOB_SIZE

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(LEVEL_TRACK_HEIGHT)
                                        .clip(CircleShape)
                                        .background(on.copy(alpha = 0.24f))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(LEVEL_TRACK_HEIGHT)
                                        .clip(CircleShape)
                                        .background(on)
                                )
                                Box(
                                    modifier = Modifier
                                        .offset(x = travel * fraction)
                                        .size(LEVEL_KNOB_SIZE)
                                        .clip(CircleShape)
                                        .background(on)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

