package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.MixCodec
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.feature.home.R

/**
 * The picker itself, with no container of its own.
 *
 * Compact widths wrap this in a [SoundBottomSheet]; expanded widths render it
 * directly as the right pane. Keeping one composable is deliberate — two
 * pickers would drift apart in both mix rules and translations.
 */
@Composable
fun SoundPickerContent(
    sounds: List<Sound>,
    activeMix: List<ActiveSound>,
    onToggleSound: (Sound) -> Unit,
    onLevelChange: (String, Float) -> Unit,
    onLevelChangeFinished: (String) -> Unit,
    columns: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sound_picker_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Three columns is the default for a twelve-sound catalogue on full width:
        // at two, only four tiles would be visible and most options would be below
        // the fold. Narrower containers, such as a side pane on tablets, should pass 2.
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The picker deliberately stays open on tap: building a mix takes
            // several taps, so the user leaves when they are done.
            items(sounds) { sound ->
                val active = activeMix.firstOrNull { it.sound.id == sound.id }
                SoundCard(
                    sound = sound,
                    isActive = active != null,
                    level = active?.level ?: 1f,
                    canDeactivate = activeMix.size > 1,
                    canActivate = activeMix.size < MixCodec.MAX_ACTIVE_SOUNDS,
                    onToggle = { onToggleSound(sound) },
                    onLevelChange = { onLevelChange(sound.id, it) },
                    onLevelChangeFinished = { onLevelChangeFinished(sound.id) }
                )
            }
        }
    }
}
