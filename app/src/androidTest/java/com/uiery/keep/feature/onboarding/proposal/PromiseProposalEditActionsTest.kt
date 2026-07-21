package com.uiery.keep.feature.onboarding.proposal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromiseProposalEditActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editActionsShareOneRowAndKeepTheirDestinations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clicks = mutableListOf<ProposalPicker>()
        val timeText = context.getString(R.string.first_promise_change_time)
        val daysText = context.getString(R.string.first_promise_change_days)
        val appText = context.getString(R.string.first_promise_change_app)

        composeRule.setContent {
            KeepTheme {
                ProposalEditActions(onEdit = { picker: ProposalPicker -> clicks += picker })
            }
        }

        val timeBounds = composeRule.onNodeWithText(timeText).fetchSemanticsNode().boundsInRoot
        val daysBounds = composeRule.onNodeWithText(daysText).fetchSemanticsNode().boundsInRoot
        val appBounds = composeRule.onNodeWithText(appText).fetchSemanticsNode().boundsInRoot

        assertEquals(timeBounds.center.y, daysBounds.center.y, 1f)
        assertEquals(daysBounds.center.y, appBounds.center.y, 1f)
        assertTrue(timeBounds.center.x < daysBounds.center.x)
        assertTrue(daysBounds.center.x < appBounds.center.x)

        composeRule.onNodeWithText(timeText).performClick()
        composeRule.onNodeWithText(daysText).performClick()
        composeRule.onNodeWithText(appText).performClick()

        assertEquals(
            listOf(ProposalPicker.StartTime, ProposalPicker.RepeatDays, ProposalPicker.App),
            clicks,
        )
    }
}
