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

    private val timeValue = "오후 11:00"
    private val daysValue = "월 · 화 · 수 · 목 · 금"
    private val appValue = "Instagram"

    @Test
    fun editActionsStackFullWidthAndKeepTheirDestinations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clicks = mutableListOf<ProposalPicker>()
        val timeText = context.getString(R.string.first_promise_change_time)
        val daysText = context.getString(R.string.first_promise_change_days)
        val appText = context.getString(R.string.first_promise_change_app)

        composeRule.setContent {
            KeepTheme {
                ProposalEditActions(
                    timeValue = timeValue,
                    daysValue = daysValue,
                    appValue = appValue,
                    onEdit = { picker: ProposalPicker -> clicks += picker },
                )
            }
        }

        val timeBounds = composeRule.onNodeWithText(timeText).fetchSemanticsNode().boundsInRoot
        val daysBounds = composeRule.onNodeWithText(daysText).fetchSemanticsNode().boundsInRoot
        val appBounds = composeRule.onNodeWithText(appText).fetchSemanticsNode().boundsInRoot

        // 3분할 가로 배치는 칸 폭이 라벨보다 좁아 글자가 잘렸다. 세로로 세워 각 행이 전폭을 쓴다.
        assertTrue(timeBounds.center.y < daysBounds.center.y)
        assertTrue(daysBounds.center.y < appBounds.center.y)
        assertEquals(timeBounds.left, daysBounds.left, 1f)
        assertEquals(daysBounds.left, appBounds.left, 1f)

        composeRule.onNodeWithText(timeText).performClick()
        composeRule.onNodeWithText(daysText).performClick()
        composeRule.onNodeWithText(appText).performClick()

        assertEquals(
            listOf(ProposalPicker.StartTime, ProposalPicker.RepeatDays, ProposalPicker.App),
            clicks,
        )
    }

    @Test
    fun eachActionShowsTheValueItChanges() {
        composeRule.setContent {
            KeepTheme {
                ProposalEditActions(
                    timeValue = timeValue,
                    daysValue = daysValue,
                    appValue = appValue,
                    onEdit = {},
                )
            }
        }

        composeRule.onNodeWithText(timeValue).assertExists()
        composeRule.onNodeWithText(daysValue).assertExists()
        composeRule.onNodeWithText(appValue).assertExists()
    }
}
