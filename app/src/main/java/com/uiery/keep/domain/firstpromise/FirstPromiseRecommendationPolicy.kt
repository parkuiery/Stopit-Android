package com.uiery.keep.domain.firstpromise

import com.uiery.keep.domain.usageinsight.OnboardingUsageProfile

object FirstPromiseRecommendationPolicy {
    const val DURATION_MINUTES = 30

    private val defaultRepeatDays = (1..7).toSet()

    fun defaultStartMinutes(goal: FirstPromiseGoal): Int = when (goal) {
        FirstPromiseGoal.Sleep -> 23 * 60
        FirstPromiseGoal.Focus -> 21 * 60
        FirstPromiseGoal.Study -> 19 * 60
        FirstPromiseGoal.FreeTime -> 20 * 60
        FirstPromiseGoal.Unspecified -> 21 * 60
    }

    fun fromProfile(
        draftId: String,
        goal: FirstPromiseGoal,
        profile: OnboardingUsageProfile,
        repeatDays: Set<Int> = defaultRepeatDays,
    ): FirstPromiseProposal {
        require(profile.dataQuality != UsageDataQuality.Insufficient) {
            "Insufficient usage does not contain recommendation evidence"
        }

        val startMinutes = when (profile.dataQuality) {
            UsageDataQuality.Full -> profile.suggestedStartMinutes
            UsageDataQuality.UsageOnly -> defaultStartMinutes(goal)
            UsageDataQuality.Insufficient -> error("Validated above")
        }
        val reason = when (profile.dataQuality) {
            UsageDataQuality.Full -> RecommendationReason.ObservedPeak(
                patternType = profile.patternType,
                usageCoverageDays = profile.usageCoverageDays,
                eventCoverageDays = profile.eventCoverageDays,
                startMinutes = startMinutes,
            )

            UsageDataQuality.UsageOnly -> RecommendationReason.GoalDefault(
                patternType = UsagePatternType.TopApp,
                goal = goal,
                startMinutes = startMinutes,
                usageCoverageDays = profile.usageCoverageDays,
            )

            UsageDataQuality.Insufficient -> error("Validated above")
        }

        return proposal(
            draftId = draftId,
            goal = goal,
            packageName = profile.packageName,
            appLabel = profile.appLabel,
            startMinutes = startMinutes,
            repeatDays = repeatDays,
            source = FirstPromiseSource.Personalized,
            reason = reason,
        )
    }

    fun fromSelection(
        draftId: String,
        goal: FirstPromiseGoal,
        packageName: String,
        appLabel: String,
        startMinutes: Int = defaultStartMinutes(goal),
        repeatDays: Set<Int> = defaultRepeatDays,
    ): FirstPromiseProposal {
        val isGoalTemplate = goal != FirstPromiseGoal.Unspecified
        val source = if (isGoalTemplate) {
            FirstPromiseSource.GoalTemplate
        } else {
            FirstPromiseSource.Manual
        }
        val reason = if (isGoalTemplate) {
            RecommendationReason.GoalDefault(
                patternType = UsagePatternType.Manual,
                goal = goal,
                startMinutes = startMinutes,
                usageCoverageDays = 0,
            )
        } else {
            RecommendationReason.Manual
        }

        return proposal(
            draftId = draftId,
            goal = goal,
            packageName = packageName,
            appLabel = appLabel,
            startMinutes = startMinutes,
            repeatDays = repeatDays,
            source = source,
            reason = reason,
        )
    }

    private fun proposal(
        draftId: String,
        goal: FirstPromiseGoal,
        packageName: String,
        appLabel: String,
        startMinutes: Int,
        repeatDays: Set<Int>,
        source: FirstPromiseSource,
        reason: RecommendationReason,
    ): FirstPromiseProposal {
        require(packageName.isNotBlank()) { "Package name must not be empty" }
        require(startMinutes in 0..1439) { "Start time must be within a local day" }
        require(repeatDays.isNotEmpty()) { "At least one repeat day is required" }

        return FirstPromiseProposal(
            draft = FirstPromiseDraft(
                draftId = draftId,
                goal = goal,
                packageName = packageName,
                appLabel = appLabel,
                startMinutes = startMinutes,
                repeatDays = repeatDays.toSet(),
                source = source,
            ),
            reason = reason,
        )
    }
}
