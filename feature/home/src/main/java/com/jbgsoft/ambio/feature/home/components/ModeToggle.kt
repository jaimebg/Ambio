package com.jbgsoft.ambio.feature.home.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

    SingleChoiceSegmentedButtonRow(modifier = modifier.testTag("modeToggle")) {
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
                        },
                        // SingleChoiceSegmentedButtonRow measures itself at
                        // Modifier.width(IntrinsicSize.Min), and a wrapping
                        // Text's minimum intrinsic width is the width of its
                        // longest unbreakable run. A Latin word has no internal
                        // break point, so that run is the whole word - but CJK
                        // permits a break between almost any two characters,
                        // collapsing the run to a glyph or two and the pill's
                        // shape along with it. Forbidding wrap makes the whole
                        // label the unbreakable run, so minimum intrinsic width
                        // becomes the full single-line width in every script.
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
