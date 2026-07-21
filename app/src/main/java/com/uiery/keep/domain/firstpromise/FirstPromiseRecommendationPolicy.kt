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
                patternType = profile.patternType,
                goal = goal,
                startMinutes = startMinutes,
                usageCoverageDays = profile.usageCoverageDays,
                eventCoverageDays = profile.eventCoverageDays,
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
                eventCoverageDays = 0,
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

    fun toReasonRef(proposal: FirstPromiseProposal): RecommendationReasonRef {
        val draft = proposal.draft
        require(FirstPromiseDraftInvariant.isValid(draft)) { "Proposal contains an invalid draft" }

        val reasonRef = when (val reason = proposal.reason) {
            is RecommendationReason.ObservedPeak -> {
                require(reason.startMinutes == draft.startMinutes) {
                    "Observed reason must explain the proposed start"
                }
                RecommendationReasonRef(
                    patternType = reason.patternType,
                    usageCoverageDays = reason.usageCoverageDays,
                    eventCoverageDays = reason.eventCoverageDays,
                    isGoalDefault = false,
                    selectedStartMinutes = reason.startMinutes,
                )
            }

            is RecommendationReason.GoalDefault -> {
                require(reason.goal == draft.goal && reason.startMinutes == draft.startMinutes) {
                    "Goal reason must explain the proposed goal and start"
                }
                RecommendationReasonRef(
                    patternType = reason.patternType,
                    usageCoverageDays = reason.usageCoverageDays,
                    eventCoverageDays = reason.eventCoverageDays,
                    isGoalDefault = true,
                    selectedStartMinutes = reason.startMinutes,
                )
            }

            RecommendationReason.Manual -> RecommendationReasonRef(
                patternType = UsagePatternType.Manual,
                usageCoverageDays = 0,
                eventCoverageDays = 0,
                isGoalDefault = false,
                selectedStartMinutes = draft.startMinutes,
            )
        }

        require(isValidReasonRef(draft.source, reasonRef)) {
            "Recommendation reason does not match the proposal source"
        }
        return reasonRef
    }

    internal fun isValidReasonRef(
        source: FirstPromiseSource,
        reason: RecommendationReasonRef,
    ): Boolean {
        if (
            reason.usageCoverageDays !in 0..7 ||
            reason.eventCoverageDays !in 0..7 ||
            reason.selectedStartMinutes !in 0..1439
        ) {
            return false
        }
        return when (source) {
            FirstPromiseSource.Personalized -> if (reason.isGoalDefault) {
                reason.patternType == UsagePatternType.TopApp &&
                    reason.usageCoverageDays in 3..7 &&
                    reason.eventCoverageDays in 0..2
            } else {
                reason.usageCoverageDays in 3..7 &&
                    reason.eventCoverageDays in 3..7 &&
                    (
                        reason.patternType == UsagePatternType.Night ||
                            reason.patternType == UsagePatternType.PeakWindow
                    )
            }

            FirstPromiseSource.GoalTemplate ->
                reason.isGoalDefault &&
                    reason.patternType == UsagePatternType.Manual &&
                    reason.usageCoverageDays == 0 &&
                    reason.eventCoverageDays == 0

            FirstPromiseSource.Manual ->
                !reason.isGoalDefault &&
                    reason.patternType == UsagePatternType.Manual &&
                    reason.usageCoverageDays == 0 &&
                    reason.eventCoverageDays == 0
        }
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
        val draft = FirstPromiseDraft(
            draftId = draftId,
            goal = goal,
            packageName = packageName,
            appLabel = appLabel,
            startMinutes = startMinutes,
            repeatDays = repeatDays.toSet(),
            source = source,
        )
        require(FirstPromiseDraftInvariant.isValid(draft)) { "Invalid first-promise draft" }

        val proposal = FirstPromiseProposal(
            draft = draft,
            reason = reason,
        )
        toReasonRef(proposal)
        return proposal
    }
}

internal object FirstPromiseDraftInvariant {
    fun isValid(draft: FirstPromiseDraft): Boolean =
        draft.draftId.isNotBlank() &&
            draft.packageName.isNotBlank() &&
            draft.appLabel.isNotBlank() &&
            draft.startMinutes in 0..1439 &&
            draft.repeatDays.isNotEmpty() &&
            draft.repeatDays.all { it in 1..7 } &&
            when (draft.source) {
                FirstPromiseSource.Personalized -> true
                FirstPromiseSource.GoalTemplate -> draft.goal != FirstPromiseGoal.Unspecified
                FirstPromiseSource.Manual -> draft.goal == FirstPromiseGoal.Unspecified
            }

    fun isValidForState(
        draft: FirstPromiseDraft,
        expectedGoal: FirstPromiseGoal,
        expectedPath: FirstPromisePath,
    ): Boolean =
        isValid(draft) &&
            draft.goal == expectedGoal &&
            when (expectedPath) {
                FirstPromisePath.Personalized -> draft.source == FirstPromiseSource.Personalized
                FirstPromisePath.Manual -> draft.source != FirstPromiseSource.Personalized
            }
}
