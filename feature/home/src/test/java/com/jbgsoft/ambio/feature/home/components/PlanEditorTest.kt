package com.jbgsoft.ambio.feature.home.components

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jbgsoft.ambio.core.domain.model.PlanRowChoice
import com.jbgsoft.ambio.core.domain.model.PlanStep
import com.jbgsoft.ambio.core.domain.model.SessionPlan
import com.jbgsoft.ambio.feature.home.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanEditorTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val six = SessionPlan(
        listOf(
            PlanStep.Focus(25), PlanStep.Break(5),
            PlanStep.Focus(25), PlanStep.Break(5),
            PlanStep.Focus(35), PlanStep.Break(15)
        ),
        repeat = false
    )

    @Test
    fun `one dropdown per step plus the terminator row`() {
        compose.setContent {
            PlanEditorContent(draft = six, onRowChoice = { _, _ -> }, onStepMinutes = { _, _ -> }, onAddStep = {}, onSave = {})
        }

        compose.onAllNodesWithContentDescription(context.getString(R.string.a11y_plan_row_choice))
            .assertCountEquals(7)
    }

    @Test
    fun `a row's dropdown states its number and current type`() {
        compose.setContent {
            PlanEditorContent(draft = six, onRowChoice = { _, _ -> }, onStepMinutes = { _, _ -> }, onAddStep = {}, onSave = {})
        }

        val expected = context.getString(
            R.string.a11y_plan_row_state,
            2,
            context.getString(R.string.plan_step_pause)
        )
        compose.onAllNodesWithContentDescription(context.getString(R.string.a11y_plan_row_choice))[1]
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
    }

    @Test
    fun `choosing loop on a row reports the row and the choice`() {
        var reported: Pair<Int, PlanRowChoice>? = null
        compose.setContent {
            PlanEditorContent(
                draft = six,
                onRowChoice = { index, choice -> reported = index to choice },
                onStepMinutes = { _, _ -> }, onAddStep = {}, onSave = {}
            )
        }

        compose.onAllNodesWithContentDescription(context.getString(R.string.a11y_plan_row_choice))[2]
            .performClick()
        compose.onNodeWithText(context.getString(R.string.plan_step_loop)).performClick()

        assertThat(reported).isEqualTo(2 to PlanRowChoice.LOOP)
    }

    @Test
    fun `done is disabled while the draft has no focus step`() {
        compose.setContent {
            PlanEditorContent(
                draft = SessionPlan(listOf(PlanStep.Break(5)), repeat = false),
                onRowChoice = { _, _ -> }, onStepMinutes = { _, _ -> }, onAddStep = {}, onSave = {}
            )
        }

        compose.onNodeWithText(context.getString(R.string.plan_done)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.plan_needs_focus)).assertExists()
    }

    @Test
    fun `done is enabled for a valid draft and reports save`() {
        var saved = false
        compose.setContent {
            PlanEditorContent(draft = six, onRowChoice = { _, _ -> }, onStepMinutes = { _, _ -> }, onAddStep = {}, onSave = { saved = true })
        }

        compose.onNodeWithText(context.getString(R.string.plan_done)).assertIsEnabled().performClick()

        assertThat(saved).isTrue()
    }

    @Test
    fun `add step is disabled at twelve steps`() {
        compose.setContent {
            PlanEditorContent(
                draft = SessionPlan(List(12) { PlanStep.Focus(1) }, repeat = false),
                onRowChoice = { _, _ -> }, onStepMinutes = { _, _ -> }, onAddStep = {}, onSave = {}
            )
        }

        compose.onNodeWithText(context.getString(R.string.plan_add_step)).assertIsNotEnabled()
    }

    @Test
    fun `the plus on a break row reports that row's new minutes`() {
        var reported: Pair<Int, Int>? = null
        compose.setContent {
            PlanEditorContent(
                draft = six, onRowChoice = { _, _ -> },
                onStepMinutes = { index, minutes -> reported = index to minutes },
                onAddStep = {}, onSave = {}
            )
        }

        compose.onAllNodesWithContentDescription(context.getString(R.string.a11y_increase_pause))[0]
            .performClick()

        assertThat(reported).isEqualTo(1 to 10)
    }
}
