package com.uiery.keep.feature.lockhistory.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.PeriodType
import com.uiery.keep.feature.lockhistory.buildLockHistoryPerformanceReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockHistoryPerformanceReportAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryCardExposesMergedTalkBackDescriptionForPerformanceCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val totalDurationMillis = 3_600_000L + 15 * 60_000L
        val sessionCount = 3
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.WEEK,
            totalDurationMillis = totalDurationMillis,
            sessionCount = sessionCount,
            topApps = listOf(context.packageName to 2),
        )
        val durationText = context.getString(R.string.lock_history_duration_format, 1, 15)
        val expectedDescription = listOf(
            context.getString(R.string.lock_history_performance_week_headline, durationText),
            context.getString(R.string.lock_history_performance_sessions_supporting, sessionCount),
            context.getString(R.string.lock_history_total_duration),
            durationText,
            context.getString(R.string.lock_history_session_count),
            context.getString(R.string.lock_history_session_count_value, sessionCount),
        ).joinToString(separator = ". ")

        composeRule.setContent {
            KeepTheme {
                LockHistorySummaryCard(
                    totalDuration = totalDurationMillis,
                    sessionCount = sessionCount,
                    report = report,
                )
            }
        }

        composeRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun topAppsCardExposesPositiveTalkBackDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val topApps = listOf(context.packageName to 2)
        val report = buildLockHistoryPerformanceReport(
            periodType = PeriodType.WEEK,
            totalDurationMillis = 30 * 60_000L,
            sessionCount = 2,
            topApps = topApps,
        )
        val appLabel = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        val expectedDescription = listOf(
            context.getString(R.string.lock_history_top_apps_title),
            context.getString(R.string.lock_history_top_apps_positive_supporting),
            "#1",
            appLabel,
            context.getString(R.string.lock_history_block_count, 2),
        ).joinToString(separator = ". ")

        composeRule.setContent {
            KeepTheme {
                LockHistoryTopApps(
                    topApps = topApps,
                    report = report,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }
}
