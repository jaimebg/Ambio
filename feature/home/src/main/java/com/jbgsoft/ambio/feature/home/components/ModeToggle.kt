package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
    val labels = options.map { mode ->
        when (mode) {
            AppMode.TIMER -> stringResource(R.string.mode_timer)
            AppMode.AMBIENT -> stringResource(R.string.mode_ambient)
        }
    }

    // SegmentedButton lays its label out through a custom Layout whose measure
    // policy does not override minIntrinsicWidth/maxIntrinsicWidth, so it falls
    // back to Compose's generic approximation - which under-reports how wide
    // the label actually needs to be. SingleChoiceSegmentedButtonRow sizes
    // itself from that number via Modifier.width(IntrinsicSize.Min) and then
    // splits it equally across segments via weight(1f), so a long label can be
    // clipped even once it is forbidden from wrapping (see the label's own
    // maxLines/softWrap below). Measuring both labels directly and enforcing
    // that as a floor on every segment's width sidesteps the unreliable
    // intrinsic path rather than depending on it.
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val layoutDirection = LocalLayoutDirection.current
    val minSegmentWidth = with(LocalDensity.current) {
        val widestLabelWidth = labels.maxOf { textMeasurer.measure(it, labelStyle).size.width }
        val horizontalContentPadding = SegmentedButtonDefaults.ContentPadding
            .let { it.calculateStartPadding(layoutDirection) + it.calculateEndPadding(layoutDirection) }
        // Either segment can be the selected one, and only the selected segment
        // reserves space for the animated checkmark - so the floor has to cover
        // that space too, not just the wider of the two plain labels.
        widestLabelWidth.toDp() + horizontalContentPadding + SegmentedButtonDefaults.IconSize + 8.dp
    }

    SingleChoiceSegmentedButtonRow(modifier = modifier.testTag("modeToggle")) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                modifier = Modifier.widthIn(min = minSegmentWidth),
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                ),
                onClick = { onModeSelected(mode) },
                selected = mode == selectedMode,
                label = {
                    Text(
                        text = labels[index],
                        modifier = Modifier.testTag("modeToggleLabel_${mode.name}"),
                        // The row can only stop wrapping the label if it knows
                        // to ask for the whole thing on one line; the width
                        // floor above is what makes that line actually fit.
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
