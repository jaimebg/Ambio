package com.jbgsoft.ambio.feature.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan

private const val LOOP_GLYPH = "↻"
private const val END_GLYPH = "✓"
private const val SEPARATOR = " · "

/**
 * The one place the summary's shape lives: minutes in order, then how the plan
 * ends. Pass the colours to tint focus and break minutes; leave them out and the
 * result is the same line unstyled, which is what [planSummaryText] returns.
 */
private fun planSummary(
    plan: SessionPlan,
    focusColor: Color? = null,
    breakColor: Color? = null
): AnnotatedString = buildAnnotatedString {
    plan.steps.forEachIndexed { index, step ->
        if (index > 0) append(SEPARATOR)
        val color = if (step is PlanStep.Focus) focusColor else breakColor
        if (color == null) {
            append(step.minutes.toString())
        } else {
            withStyle(SpanStyle(color = color)) { append(step.minutes.toString()) }
        }
    }
    append(" ")
    append(if (plan.repeat) LOOP_GLYPH else END_GLYPH)
}

/** The plain-text form, and exactly the characters the styled line shows. */
fun planSummaryText(plan: SessionPlan): String = planSummary(plan).text

/**
 * One line, focus minutes in the foreground colour and break minutes dimmed,
 * so the alternation reads without labels.
 *
 * No content description: the text is the description, and overriding it would
 * have a screen reader say "Session plan" in place of the plan.
 */
@Composable
fun PlanSummary(plan: SessionPlan, modifier: Modifier = Modifier) {
    val focusColor = MaterialTheme.colorScheme.onSurface
    val breakColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(plan, focusColor, breakColor) {
        planSummary(plan, focusColor, breakColor)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
