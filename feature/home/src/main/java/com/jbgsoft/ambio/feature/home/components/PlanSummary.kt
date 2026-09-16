package com.jbgsoft.ambio.feature.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.feature.home.R

private const val LOOP_GLYPH = "↻"
private const val END_GLYPH = "✓"
private const val SEPARATOR = " · "

/** The plain-text form: minutes in order, then how the plan ends. Also what tests match. */
fun planSummaryText(plan: SessionPlan): String =
    plan.steps.joinToString(SEPARATOR) { it.minutes.toString() } +
        " " + if (plan.repeat) LOOP_GLYPH else END_GLYPH

/**
 * One line, focus minutes in the foreground colour and break minutes dimmed,
 * so the alternation reads without labels.
 */
@Composable
fun PlanSummary(plan: SessionPlan, modifier: Modifier = Modifier) {
    val focusColor = MaterialTheme.colorScheme.onSurface
    val breakColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        plan.steps.forEachIndexed { index, step ->
            if (index > 0) append(SEPARATOR)
            withStyle(SpanStyle(color = if (step is PlanStep.Focus) focusColor else breakColor)) {
                append(step.minutes.toString())
            }
        }
        append(" ")
        append(if (plan.repeat) LOOP_GLYPH else END_GLYPH)
    }
    val description = stringResource(R.string.a11y_plan_summary)
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { contentDescription = description }
    )
}
