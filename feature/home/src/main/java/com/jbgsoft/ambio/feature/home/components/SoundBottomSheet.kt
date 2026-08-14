package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.MixCodec
import com.jbgsoft.ambio.core.domain.model.Sound
import com.jbgsoft.ambio.feature.home.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundBottomSheet(
    showSheet: Boolean,
    sounds: List<Sound>,
    activeMix: List<ActiveSound>,
    onToggleSound: (Sound) -> Unit,
    onLevelChange: (String, Float) -> Unit,
    onLevelChangeFinished: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.sound_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyVerticalGrid(
                    // Three columns for a thirteen-sound catalogue: at two, only
                    // four tiles were visible and most of the options were below
                    // the fold.
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The sheet deliberately stays open on tap: building a mix takes
                    // several taps, so the user dismisses it when they are done.
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
    }
}
