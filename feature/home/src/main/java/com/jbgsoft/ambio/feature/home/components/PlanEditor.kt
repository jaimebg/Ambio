package com.jbgsoft.ambio.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jbgsoft.ambio.core.domain.model.PlanRowChoice
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.feature.home.R

/**
 * One row per step and a terminator row after them. Choosing Loop or End on any
 * row makes it the terminator and the rows after it disappear, so the user
 * always sees how the plan ends: there is no implicit end.
 */
@Composable
fun PlanEditorContent(
    draft: SessionPlan,
    onRowChoice: (index: Int, choice: PlanRowChoice) -> Unit,
    onStepMinutes: (index: Int, minutes: Int) -> Unit,
    onAddStep: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.plan_editor_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            draft.steps.forEachIndexed { index, step ->
                StepRow(
                    index = index,
                    step = step,
                    onChoice = { onRowChoice(index, it) },
                    onMinutes = { onStepMinutes(index, it) }
                )
            }
            TerminatorRow(
                index = draft.steps.size,
                repeat = draft.repeat,
                onChoice = { onRowChoice(draft.steps.size, it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!draft.isValid) {
            Text(
                text = stringResource(R.string.plan_needs_focus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onAddStep,
                enabled = draft.steps.size < SessionPlan.MAX_STEPS
            ) {
                Text(stringResource(R.string.plan_add_step))
            }
            Button(onClick = onSave, enabled = draft.isValid) {
                Text(stringResource(R.string.plan_done))
            }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: PlanStep,
    onChoice: (PlanRowChoice) -> Unit,
    onMinutes: (Int) -> Unit
) {
    val isFocus = step is PlanStep.Focus
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowNumber(index)
        ChoiceDropdown(
            current = if (isFocus) PlanRowChoice.FOCUS else PlanRowChoice.BREAK,
            onChoice = onChoice
        )
        Spacer(modifier = Modifier.width(8.dp))
        NumberStepper(
            value = step.minutes,
            onValueChange = onMinutes,
            onValueChangeFinished = {},
            minValue = PlanStep.MIN_MINUTES,
            maxValue = step.maxMinutes,
            step = 5,
            decreaseDescription = if (isFocus) R.string.a11y_decrease_focus else R.string.a11y_decrease_pause,
            increaseDescription = if (isFocus) R.string.a11y_increase_focus else R.string.a11y_increase_pause,
            isCompact = true
        )
    }
}

@Composable
private fun TerminatorRow(index: Int, repeat: Boolean, onChoice: (PlanRowChoice) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowNumber(index)
        ChoiceDropdown(
            current = if (repeat) PlanRowChoice.LOOP else PlanRowChoice.END,
            onChoice = onChoice
        )
    }
}

@Composable
private fun RowNumber(index: Int) {
    Text(
        text = (index + 1).toString(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(28.dp)
    )
}

@Composable
private fun ChoiceDropdown(current: PlanRowChoice, onChoice: (PlanRowChoice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.a11y_plan_row_choice)
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(current.label())
        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        PlanRowChoice.entries.forEach { choice ->
            DropdownMenuItem(
                text = { Text(choice.label()) },
                onClick = {
                    expanded = false
                    onChoice(choice)
                }
            )
        }
    }
}

@Composable
private fun PlanRowChoice.label(): String = stringResource(
    when (this) {
        PlanRowChoice.FOCUS -> R.string.plan_step_focus
        PlanRowChoice.BREAK -> R.string.plan_step_pause
        PlanRowChoice.LOOP -> R.string.plan_step_loop
        PlanRowChoice.END -> R.string.plan_step_end
    }
)

/** Compact widths: a sheet, like the sound picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorSheet(
    draft: SessionPlan?,
    onRowChoice: (Int, PlanRowChoice) -> Unit,
    onStepMinutes: (Int, Int) -> Unit,
    onAddStep: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (draft == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        PlanEditorContent(
            draft = draft,
            onRowChoice = onRowChoice,
            onStepMinutes = onStepMinutes,
            onAddStep = onAddStep,
            onSave = onSave,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

/** Expanded widths: the right pane is already the sound picker, so the editor is a dialog. */
@Composable
fun PlanEditorDialog(
    draft: SessionPlan?,
    onRowChoice: (Int, PlanRowChoice) -> Unit,
    onStepMinutes: (Int, Int) -> Unit,
    onAddStep: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (draft == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            PlanEditorContent(
                draft = draft,
                onRowChoice = onRowChoice,
                onStepMinutes = onStepMinutes,
                onAddStep = onAddStep,
                onSave = onSave
            )
        }
    )
}
