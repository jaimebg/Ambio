package com.jbgsoft.ambio.feature.home.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jbgsoft.ambio.core.domain.model.AppMode
import com.jbgsoft.ambio.feature.home.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeToggle(
    selectedMode: AppMode,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(AppMode.TIMER, AppMode.AMBIENT)

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                ),
                onClick = { onModeSelected(mode) },
                selected = mode == selectedMode,
                label = {
                    Text(
                        text = when (mode) {
                            AppMode.TIMER -> stringResource(R.string.mode_timer)
                            AppMode.AMBIENT -> stringResource(R.string.mode_ambient)
                        }
                    )
                }
            )
        }
    }
}
