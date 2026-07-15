package com.uiery.keep.domain.firstpromise

import com.uiery.keep.domain.usageinsight.OnboardingUsageProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class FirstPromiseRecommendationPolicyTest {
    @Test
    fun `goal defaults are fixed local times`() {
        val expected = mapOf(
            FirstPromiseGoal.Sleep to 23 * 60,
            FirstPromiseGoal.Focus to 21 * 60,
            FirstPromiseGoal.Study to 19 * 60,
            FirstPromiseGoal.FreeTime to 20 * 60,
            FirstPromiseGoal.Unspecified to 21 * 60,
        )

        assertEquals(
            expected,
            FirstPromiseGoal.entries.associateWith(FirstPromiseRecommendationPolicy::defaultStartMinutes),
        )
    }

    @Test
    fun `full evidence beats the goal default and explains the observed peak`() {
        val profile = profile(
            startMinutes = 23 * 60 + 30,
            usageCoverageDays = 7,
            eventCoverageDays = 5,
            dataQuality = UsageDataQuality.Full,
            patternType = UsagePatternType.Night,
        )

        val proposal = FirstPromiseRecommendationPolicy.fromProfile(
            draftId = "draft-full",
            goal = FirstPromiseGoal.Study,
            profile = profile,
        )

        assertEquals("draft-full", proposal.draft.draftId)
        assertEquals(FirstPromiseSource.Personalized, proposal.draft.source)
        assertEquals(profile.packageName, proposal.draft.packageName)
        assertEquals(profile.appLabel, proposal.draft.appLabel)
        assertEquals(profile.suggestedStartMinutes, proposal.draft.startMinutes)
        assertEquals((1..7).toSet(), proposal.draft.repeatDays)
        assertEquals(30, FirstPromiseRecommendationPolicy.DURATION_MINUTES)
        assertEquals(
            RecommendationReason.ObservedPeak(
                patternType = UsagePatternType.Night,
                usageCoverageDays = 7,
                eventCoverageDays = 5,
                startMinutes = profile.suggestedStartMinutes,
            ),
            proposal.reason,
        )
    }

    @Test
    fun `usage-only evidence personalizes the app but uses and explains the goal default`() {
        val profile = profile(
            packageName = "com.example.reader",
            appLabel = "Reader",
            startMinutes = 4 * 60,
            usageCoverageDays = 4,
            eventCoverageDays = 1,
            dataQuality = UsageDataQuality.UsageOnly,
            patternType = UsagePatternType.TopApp,
        )

        val proposal = FirstPromiseRecommendationPolicy.fromProfile(
            draftId = "draft-usage-only",
            goal = FirstPromiseGoal.Focus,
            profile = profile,
        )

        assertEquals(FirstPromiseSource.Personalized, proposal.draft.source)
        assertEquals(profile.packageName, proposal.draft.packageName)
        assertEquals(21 * 60, proposal.draft.startMinutes)
        assertEquals(
            RecommendationReason.GoalDefault(
                patternType = UsagePatternType.TopApp,
                goal = FirstPromiseGoal.Focus,
                startMinutes = 21 * 60,
                usageCoverageDays = 4,
                eventCoverageDays = 1,
            ),
            proposal.reason,
        )
    }

    @Test
    fun `selected goal and app after insufficient evidence use a manual-pattern goal template`() {
        val proposal = FirstPromiseRecommendationPolicy.fromSelection(
            draftId = "draft-template",
            goal = FirstPromiseGoal.Sleep,
            packageName = "com.example.video",
            appLabel = "Video",
        )

        assertEquals(FirstPromiseSource.GoalTemplate, proposal.draft.source)
        assertEquals(23 * 60, proposal.draft.startMinutes)
        assertEquals(
            RecommendationReason.GoalDefault(
                patternType = UsagePatternType.Manual,
                goal = FirstPromiseGoal.Sleep,
                startMinutes = 23 * 60,
                usageCoverageDays = 0,
                eventCoverageDays = 0,
            ),
            proposal.reason,
        )
    }

    @Test
    fun `direct app and time without a goal are fully manual`() {
        val proposal = FirstPromiseRecommendationPolicy.fromSelection(
            draftId = "draft-manual",
            goal = FirstPromiseGoal.Unspecified,
            packageName = "com.example.social",
            appLabel = "Social",
            startMinutes = 20 * 60 + 15,
        )

        assertEquals(FirstPromiseSource.Manual, proposal.draft.source)
        assertEquals(20 * 60 + 15, proposal.draft.startMinutes)
        assertEquals(RecommendationReason.Manual, proposal.reason)
    }

    @Test
    fun `reason and draft copy the same immutable package-independent evidence`() {
        val profile = profile(
            averageDailyMinutes = 102,
            startMinutes = 22 * 60,
            usageCoverageDays = 6,
            eventCoverageDays = 4,
            dataQuality = UsageDataQuality.Full,
            patternType = UsagePatternType.Night,
        )
        val proposal = FirstPromiseRecommendationPolicy.fromProfile(
            draftId = "draft-evidence",
            goal = FirstPromiseGoal.FreeTime,
            profile = profile,
        )
        val reason = proposal.reason as RecommendationReason.ObservedPeak

        assertEquals(proposal.draft.startMinutes, reason.startMinutes)
        assertEquals(profile.usageCoverageDays, reason.usageCoverageDays)
        assertEquals(profile.eventCoverageDays, reason.eventCoverageDays)
        assertFalse(
            reason.javaClass.declaredFields.any {
                it.name == "packageName" || it.name == "averageDailyMinutes"
            },
        )
        assertFalse(proposal.javaClass.declaredFields.any { it.name == "averageDailyMinutes" })

        val edited = proposal.copy(draft = proposal.draft.copy(startMinutes = 18 * 60))

        assertEquals(18 * 60, edited.draft.startMinutes)
        assertEquals(22 * 60, reason.startMinutes)
        assertEquals(22 * 60, profile.suggestedStartMinutes)
        assertEquals(102, profile.averageDailyMinutes)
    }

    @Test
    fun `invalid schedule inputs are rejected`() {
        val validProfile = profile()

        assertThrows(IllegalArgumentException::class.java) {
            FirstPromiseRecommendationPolicy.fromProfile(
                draftId = " ",
                goal = FirstPromiseGoal.Focus,
                profile = validProfile,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirstPromiseRecommendationPolicy.fromProfile(
                draftId = "invalid-start",
                goal = FirstPromiseGoal.Focus,
                profile = validProfile.copy(suggestedStartMinutes = 24 * 60),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirstPromiseRecommendationPolicy.fromSelection(
                draftId = "empty-package",
                goal = FirstPromiseGoal.Focus,
                packageName = " ",
                appLabel = "App",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirstPromiseRecommendationPolicy.fromSelection(
                draftId = "empty-label",
                goal = FirstPromiseGoal.Focus,
                packageName = "com.example.app",
                appLabel = " ",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirstPromiseRecommendationPolicy.fromSelection(
                draftId = "empty-days",
                goal = FirstPromiseGoal.Focus,
                packageName = "com.example.app",
                appLabel = "App",
                repeatDays = emptySet(),
            )
        }
        listOf(setOf(0), setOf(8), setOf(1, 8)).forEach { repeatDays ->
            assertThrows(IllegalArgumentException::class.java) {
                FirstPromiseRecommendationPolicy.fromSelection(
                    draftId = "invalid-days",
                    goal = FirstPromiseGoal.Focus,
                    packageName = "com.example.app",
                    appLabel = "App",
                    repeatDays = repeatDays,
                )
            }
        }
    }

    @Test
    fun `valid repeat boundaries are copied away from caller mutation`() {
        val repeatDays = mutableSetOf(1, 7)
        val proposal = FirstPromiseRecommendationPolicy.fromSelection(
            draftId = "copied-days",
            goal = FirstPromiseGoal.Unspecified,
            packageName = "com.example.app",
            appLabel = "App",
            startMinutes = 0,
            repeatDays = repeatDays,
        )

        repeatDays.clear()

        assertEquals(setOf(1, 7), proposal.draft.repeatDays)
        assertEquals(0, proposal.draft.startMinutes)
    }

    @Test
    fun `typed reason mapping preserves only exact package-independent evidence`() {
        val observed = FirstPromiseRecommendationPolicy.fromProfile(
            draftId = "observed",
            goal = FirstPromiseGoal.Sleep,
            profile = profile(
                startMinutes = 22 * 60,
                usageCoverageDays = 7,
                eventCoverageDays = 4,
                dataQuality = UsageDataQuality.Full,
                patternType = UsagePatternType.Night,
            ),
        )
        val usageOnly = FirstPromiseRecommendationPolicy.fromProfile(
            draftId = "usage-only",
            goal = FirstPromiseGoal.Study,
            profile = profile(
                usageCoverageDays = 5,
                eventCoverageDays = 1,
                dataQuality = UsageDataQuality.UsageOnly,
                patternType = UsagePatternType.TopApp,
            ),
        )
        val goalTemplate = FirstPromiseRecommendationPolicy.fromSelection(
            draftId = "goal-template",
            goal = FirstPromiseGoal.Focus,
            packageName = "com.example.app",
            appLabel = "App",
        )
        val manual = FirstPromiseRecommendationPolicy.fromSelection(
            draftId = "manual",
            goal = FirstPromiseGoal.Unspecified,
            packageName = "com.example.app",
            appLabel = "App",
            startMinutes = 18 * 60,
        )

        assertEquals(
            RecommendationReasonRef(
                patternType = UsagePatternType.Night,
                usageCoverageDays = 7,
                eventCoverageDays = 4,
                isGoalDefault = false,
                selectedStartMinutes = 22 * 60,
            ),
            FirstPromiseRecommendationPolicy.toReasonRef(observed),
        )
        assertEquals(
            RecommendationReasonRef(
                patternType = UsagePatternType.TopApp,
                usageCoverageDays = 5,
                eventCoverageDays = 1,
                isGoalDefault = true,
                selectedStartMinutes = 19 * 60,
            ),
            FirstPromiseRecommendationPolicy.toReasonRef(usageOnly),
        )
        assertEquals(
            RecommendationReasonRef(
                patternType = UsagePatternType.Manual,
                usageCoverageDays = 0,
                eventCoverageDays = 0,
                isGoalDefault = true,
                selectedStartMinutes = 21 * 60,
            ),
            FirstPromiseRecommendationPolicy.toReasonRef(goalTemplate),
        )
        assertEquals(
            RecommendationReasonRef(
                patternType = UsagePatternType.Manual,
                usageCoverageDays = 0,
                eventCoverageDays = 0,
                isGoalDefault = false,
                selectedStartMinutes = 18 * 60,
            ),
            FirstPromiseRecommendationPolicy.toReasonRef(manual),
        )
    }

    private fun profile(
        packageName: String = "com.example.video",
        appLabel: String = "Video",
        averageDailyMinutes: Long = 90,
        startMinutes: Int = 22 * 60,
        usageCoverageDays: Int = 7,
        eventCoverageDays: Int = 7,
        dataQuality: UsageDataQuality = UsageDataQuality.Full,
        patternType: UsagePatternType = UsagePatternType.Night,
    ) = OnboardingUsageProfile(
        packageName = packageName,
        appLabel = appLabel,
        averageDailyMinutes = averageDailyMinutes,
        suggestedStartMinutes = startMinutes,
        usageCoverageDays = usageCoverageDays,
        eventCoverageDays = eventCoverageDays,
        dataQuality = dataQuality,
        patternType = patternType,
    )
}
