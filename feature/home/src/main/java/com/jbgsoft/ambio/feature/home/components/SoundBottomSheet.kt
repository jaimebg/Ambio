package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.ActiveSound
import com.jbgsoft.ambio.core.domain.model.Sound

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
            SoundPickerContent(
                sounds = sounds,
                activeMix = activeMix,
                onToggleSound = onToggleSound,
                onLevelChange = onLevelChange,
                onLevelChangeFinished = onLevelChangeFinished,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
