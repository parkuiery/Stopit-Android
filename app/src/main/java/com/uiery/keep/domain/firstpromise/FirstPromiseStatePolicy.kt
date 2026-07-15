package com.uiery.keep.domain.firstpromise

sealed interface FirstPromiseStateMutation {
    data class Changed(val state: FirstPromiseOnboardingState) : FirstPromiseStateMutation
    data object NoOp : FirstPromiseStateMutation
    data object Rejected : FirstPromiseStateMutation
}

enum class FirstPromiseEmergencyAction {
    NavigateManualSelect,
    Stay,
    WaitForPersistence,
    DisableFutureAnalysis,
    Rejected,
}

data class FirstPromiseEmergencyResult(
    val state: FirstPromiseOnboardingState,
    val action: FirstPromiseEmergencyAction,
) {
    val navigationAllowed: Boolean = action !in setOf(
        FirstPromiseEmergencyAction.WaitForPersistence,
        FirstPromiseEmergencyAction.Rejected,
    )
}

sealed interface FirstPromisePersistenceResolution {
    data class Succeeded(
        val routineId: Long,
        val scheduleState: FirstPromiseScheduleState,
    ) : FirstPromisePersistenceResolution

    data object Failed : FirstPromisePersistenceResolution
}

object FirstPromiseStatePolicy {
    private enum class MappingValidity { None, Complete, Invalid }

    private val mappedRecoveryPhases = setOf(
        FirstPromisePhase.GoalPending,
        FirstPromisePhase.UsageAccessPending,
        FirstPromisePhase.Analyzing,
        FirstPromisePhase.ManualSelectPending,
        FirstPromisePhase.DraftReady,
        FirstPromisePhase.AccessibilityPending,
        FirstPromisePhase.NotificationPending,
        FirstPromisePhase.Persisting,
        FirstPromisePhase.PersistFailed,
    )

    private val allowedTargets = mapOf(
        FirstPromisePhase.GoalPending to setOf(
            FirstPromisePhase.UsageAccessPending,
            FirstPromisePhase.ManualSelectPending,
        ),
        FirstPromisePhase.UsageAccessPending to setOf(
            FirstPromisePhase.Analyzing,
            FirstPromisePhase.ManualSelectPending,
        ),
        FirstPromisePhase.Analyzing to setOf(
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.ManualSelectPending,
        ),
        FirstPromisePhase.ManualSelectPending to setOf(
            FirstPromisePhase.ManualSelectPending,
            FirstPromisePhase.DraftReady,
        ),
        FirstPromisePhase.DraftReady to setOf(
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.AccessibilityPending,
        ),
        FirstPromisePhase.AccessibilityPending to setOf(
            FirstPromisePhase.AccessibilityPending,
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.NotificationPending,
        ),
        FirstPromisePhase.NotificationPending to setOf(
            FirstPromisePhase.NotificationPending,
            FirstPromisePhase.Persisting,
        ),
        FirstPromisePhase.Persisting to setOf(
            FirstPromisePhase.Persisting,
            FirstPromisePhase.PersistFailed,
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromisePhase.ResultEnabled,
        ),
        FirstPromisePhase.PersistFailed to setOf(
            FirstPromisePhase.PersistFailed,
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.Persisting,
        ),
        FirstPromisePhase.SchedulePermissionRequired to setOf(
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromisePhase.ResultEnabled,
            FirstPromisePhase.ResultDisabled,
        ),
        FirstPromisePhase.ResultEnabled to setOf(
            FirstPromisePhase.ResultEnabled,
            FirstPromisePhase.CompletedEnabled,
        ),
        FirstPromisePhase.ResultDisabled to setOf(
            FirstPromisePhase.ResultDisabled,
            FirstPromisePhase.CompletedDisabled,
        ),
        FirstPromisePhase.CompletedEnabled to setOf(FirstPromisePhase.CompletedEnabled),
        FirstPromisePhase.CompletedDisabled to setOf(FirstPromisePhase.CompletedDisabled),
    )

    fun transition(
        state: FirstPromiseOnboardingState,
        target: FirstPromisePhase,
    ): FirstPromiseStateMutation {
        if (target !in allowedTargets.getValue(state.phase)) {
            return FirstPromiseStateMutation.Rejected
        }
        if (target == state.phase) {
            return FirstPromiseStateMutation.NoOp
        }
        val nextState = state.copy(phase = target)
        return FirstPromiseStateMutation.Changed(
            if (target == FirstPromisePhase.CompletedEnabled || target == FirstPromisePhase.CompletedDisabled) {
                nextState.copy(
                    draft = null,
                    recommendationReasonRef = null,
                    pendingSystemAction = null,
                    usagePermissionAttempt = null,
                    analysisAttemptId = null,
                )
            } else {
                nextState
            },
        )
    }

    fun assignIfAbsent(
        state: FirstPromiseOnboardingState,
        variant: OnboardingVariant,
        version: OnboardingAssignmentVersion,
    ): FirstPromiseStateMutation =
        if (state.assignment != null) {
            FirstPromiseStateMutation.NoOp
        } else {
            FirstPromiseStateMutation.Changed(
                state.copy(assignment = variant, assignmentVersion = version),
            )
        }

    fun advanceToUsageAccess(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        transition(state, FirstPromisePhase.UsageAccessPending)

    fun chooseManualSetup(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        when (state.phase) {
            FirstPromisePhase.ManualSelectPending -> FirstPromiseStateMutation.NoOp
            FirstPromisePhase.GoalPending,
            FirstPromisePhase.UsageAccessPending,
            FirstPromisePhase.Analyzing,
            -> FirstPromiseStateMutation.Changed(state.normalAnalysisFallback())

            else -> FirstPromiseStateMutation.Rejected
        }

    fun beginUsageAnalysis(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
    ): FirstPromiseStateMutation {
        if (
            state.phase != FirstPromisePhase.UsageAccessPending ||
            state.goal == FirstPromiseGoal.Unspecified ||
            state.futureAnalysisDisabled
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        val analyzingState =
            (transition(state, FirstPromisePhase.Analyzing) as? FirstPromiseStateMutation.Changed)?.state
                ?: return FirstPromiseStateMutation.Rejected
        return beginAnalysisAttempt(analyzingState, attemptId)
    }

    fun requestAccessibility(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        transitionWithDraft(state, FirstPromisePhase.AccessibilityPending)

    fun returnToDraft(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        transitionWithDraft(state, FirstPromisePhase.DraftReady)

    fun requestNotification(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        transitionWithDraft(state, FirstPromisePhase.NotificationPending)

    fun beginPersistence(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        if (state.routineId == null && state.scheduleState == null) {
            transitionWithDraft(state, FirstPromisePhase.Persisting)
        } else {
            FirstPromiseStateMutation.Rejected
        }

    fun markPersistenceFailed(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        if (state.routineId == null && state.scheduleState == null) {
            transitionWithDraft(state, FirstPromisePhase.PersistFailed)
        } else {
            FirstPromiseStateMutation.Rejected
        }

    fun completeOnboarding(state: FirstPromiseOnboardingState): FirstPromiseStateMutation {
        if (
            state.routineId != null && state.routineId > 0L &&
            (
                state.phase == FirstPromisePhase.CompletedEnabled &&
                    state.scheduleState == FirstPromiseScheduleState.Enabled ||
                    state.phase == FirstPromisePhase.CompletedDisabled &&
                    state.scheduleState != null &&
                    state.scheduleState != FirstPromiseScheduleState.Enabled
            )
        ) {
            return FirstPromiseStateMutation.NoOp
        }
        val target = when {
            state.phase == FirstPromisePhase.ResultEnabled &&
                state.routineId != null && state.routineId > 0L &&
                state.scheduleState == FirstPromiseScheduleState.Enabled -> FirstPromisePhase.CompletedEnabled

            state.phase == FirstPromisePhase.ResultDisabled &&
                state.routineId != null && state.routineId > 0L &&
                state.scheduleState != null &&
                state.scheduleState != FirstPromiseScheduleState.Enabled -> FirstPromisePhase.CompletedDisabled

            else -> return FirstPromiseStateMutation.Rejected
        }
        return transition(state, target)
    }

    fun selectGoal(
        state: FirstPromiseOnboardingState,
        goal: FirstPromiseGoal,
        path: FirstPromisePath,
    ): FirstPromiseStateMutation {
        if (
            state.phase !in setOf(
                FirstPromisePhase.GoalPending,
                FirstPromisePhase.UsageAccessPending,
                FirstPromisePhase.ManualSelectPending,
            )
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        if (state.goal == goal && state.path == path) {
            return FirstPromiseStateMutation.NoOp
        }
        return FirstPromiseStateMutation.Changed(state.copy(goal = goal, path = path))
    }

    fun markMilestone(
        state: FirstPromiseOnboardingState,
        milestone: FirstPromiseMilestone,
    ): FirstPromiseStateMutation =
        if (milestone in state.trackedMilestones) {
            FirstPromiseStateMutation.NoOp
        } else {
            FirstPromiseStateMutation.Changed(
                state.copy(trackedMilestones = state.trackedMilestones + milestone),
            )
        }

    fun createManualDraft(
        state: FirstPromiseOnboardingState,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation {
        if (
            state.phase != FirstPromisePhase.ManualSelectPending ||
            state.path != FirstPromisePath.Manual ||
            !draftAndReasonAreValidForState(state, draft, reason)
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        val phaseMutation = transition(state, FirstPromisePhase.DraftReady)
        val phaseState = (phaseMutation as? FirstPromiseStateMutation.Changed)?.state
            ?: return FirstPromiseStateMutation.Rejected
        return FirstPromiseStateMutation.Changed(
            phaseState.copy(draft = draft, recommendationReasonRef = reason),
        )
    }

    fun editDraft(
        state: FirstPromiseOnboardingState,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation {
        val currentDraft = state.draft
        val currentReason = state.recommendationReasonRef
        if (
            state.phase != FirstPromisePhase.DraftReady ||
            currentDraft == null ||
            currentReason == null ||
            draft.draftId != currentDraft.draftId ||
            draft.source != currentDraft.source ||
            reason != currentReason.copy(selectedStartMinutes = draft.startMinutes) ||
            !draftAndReasonAreValidForState(state, draft, reason)
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        val nextState = state.copy(draft = draft, recommendationReasonRef = reason)
        return if (nextState == state) {
            FirstPromiseStateMutation.NoOp
        } else {
            FirstPromiseStateMutation.Changed(nextState)
        }
    }

    fun setPendingSystemAction(
        state: FirstPromiseOnboardingState,
        action: PendingSystemAction?,
    ): FirstPromiseStateMutation {
        if (state.phase == FirstPromisePhase.CompletedEnabled || state.phase == FirstPromisePhase.CompletedDisabled) {
            return FirstPromiseStateMutation.Rejected
        }
        return if (state.pendingSystemAction == action) {
            FirstPromiseStateMutation.NoOp
        } else {
            FirstPromiseStateMutation.Changed(state.copy(pendingSystemAction = action))
        }
    }

    fun beginAnalysisAttempt(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
    ): FirstPromiseStateMutation {
        if (
            state.phase != FirstPromisePhase.Analyzing ||
            state.futureAnalysisDisabled ||
            (state.analysisAttemptId != null && attemptId <= state.analysisAttemptId)
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        return FirstPromiseStateMutation.Changed(
            state.copy(
                analysisAttemptId = attemptId,
                draft = null,
                recommendationReasonRef = null,
            ),
        )
    }

    fun completeAnalysis(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation {
        if (
            !acceptsAnalysisAttempt(state, attemptId) ||
            state.path != FirstPromisePath.Personalized ||
            !draftAndReasonAreValidForState(state, draft, reason)
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        val phaseMutation = transition(state, FirstPromisePhase.DraftReady)
        val phaseState = (phaseMutation as? FirstPromiseStateMutation.Changed)?.state
            ?: return FirstPromiseStateMutation.Rejected
        return FirstPromiseStateMutation.Changed(
            phaseState.copy(draft = draft, recommendationReasonRef = reason),
        )
    }

    fun failAnalysis(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
    ): FirstPromiseStateMutation =
        if (acceptsAnalysisAttempt(state, attemptId)) {
            FirstPromiseStateMutation.Changed(state.normalAnalysisFallback())
        } else {
            FirstPromiseStateMutation.Rejected
        }

    fun recordPersistenceMapping(
        state: FirstPromiseOnboardingState,
        routineId: Long,
        scheduleState: FirstPromiseScheduleState,
    ): FirstPromiseStateMutation {
        if (routineId <= 0L) {
            return FirstPromiseStateMutation.Rejected
        }
        if (state.routineId != null) {
            return if (state.routineId == routineId && state.scheduleState == scheduleState) {
                FirstPromiseStateMutation.NoOp
            } else {
                FirstPromiseStateMutation.Rejected
            }
        }
        if (state.phase != FirstPromisePhase.Persisting) {
            return FirstPromiseStateMutation.Rejected
        }
        val target = if (scheduleState == FirstPromiseScheduleState.Enabled) {
            FirstPromisePhase.ResultEnabled
        } else {
            FirstPromisePhase.SchedulePermissionRequired
        }
        val phaseState = (transition(state, target) as? FirstPromiseStateMutation.Changed)?.state
            ?: return FirstPromiseStateMutation.Rejected
        return FirstPromiseStateMutation.Changed(
            phaseState.copy(
                routineId = routineId,
                scheduleState = scheduleState,
                pendingSystemAction = null,
            ),
        )
    }

    fun resolveScheduleState(
        state: FirstPromiseOnboardingState,
        routineId: Long,
        scheduleState: FirstPromiseScheduleState,
    ): FirstPromiseStateMutation {
        if (state.routineId != routineId) {
            return FirstPromiseStateMutation.Rejected
        }
        val target = if (scheduleState == FirstPromiseScheduleState.Enabled) {
            FirstPromisePhase.ResultEnabled
        } else {
            FirstPromisePhase.ResultDisabled
        }
        if (state.phase == target && state.scheduleState == scheduleState) {
            return FirstPromiseStateMutation.NoOp
        }
        if (state.phase != FirstPromisePhase.SchedulePermissionRequired) {
            return FirstPromiseStateMutation.Rejected
        }
        val phaseState = (transition(state, target) as? FirstPromiseStateMutation.Changed)?.state
            ?: return FirstPromiseStateMutation.Rejected
        return FirstPromiseStateMutation.Changed(
            phaseState.copy(
                scheduleState = scheduleState,
                pendingSystemAction = null,
            ),
        )
    }

    fun beginUsagePermissionAttempt(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
        manual: Boolean = false,
    ): FirstPromiseStateMutation {
        val currentId = state.usagePermissionAttempt?.id
        if (currentId != null && attemptId <= currentId) {
            return FirstPromiseStateMutation.Rejected
        }
        return FirstPromiseStateMutation.Changed(
            state.copy(
                usagePermissionAttempt = UsagePermissionAttempt(
                    id = attemptId,
                    launchState = UsagePermissionLaunchState.NotLaunched,
                    terminalOutcome = if (manual) UsagePermissionOutcome.Skipped else null,
                ),
            ),
        )
    }

    fun markUsagePermissionOpened(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
    ): FirstPromiseStateMutation {
        val attempt = state.usagePermissionAttempt
        if (
            attempt?.id != attemptId ||
            attempt.terminalOutcome != null ||
            attempt.launchState != UsagePermissionLaunchState.NotLaunched
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        return FirstPromiseStateMutation.Changed(
            state.copy(usagePermissionAttempt = attempt.copy(launchState = UsagePermissionLaunchState.Opened)),
        )
    }

    fun markUsagePermissionLaunchFailed(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
    ): FirstPromiseStateMutation {
        val attempt = state.usagePermissionAttempt
        if (
            attempt?.id != attemptId ||
            attempt.terminalOutcome != null ||
            attempt.launchState != UsagePermissionLaunchState.NotLaunched
        ) {
            return FirstPromiseStateMutation.Rejected
        }
        return FirstPromiseStateMutation.Changed(
            state.copy(
                usagePermissionAttempt = attempt.copy(
                    launchState = UsagePermissionLaunchState.LaunchFailed,
                    terminalOutcome = UsagePermissionOutcome.Unknown,
                ),
            ),
        )
    }

    fun recordUsagePermissionResume(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
        permissionGranted: Boolean,
        sameProcess: Boolean,
    ): FirstPromiseStateMutation {
        val attempt = state.usagePermissionAttempt
        if (attempt?.id != attemptId || attempt.terminalOutcome != null) {
            return FirstPromiseStateMutation.Rejected
        }
        val outcome = when {
            permissionGranted -> UsagePermissionOutcome.Granted
            sameProcess && attempt.launchState == UsagePermissionLaunchState.Opened -> UsagePermissionOutcome.Denied
            else -> return FirstPromiseStateMutation.Rejected
        }
        return FirstPromiseStateMutation.Changed(
            state.copy(usagePermissionAttempt = attempt.copy(terminalOutcome = outcome)),
        )
    }

    fun reconcileUsagePermissionAfterRecreation(
        state: FirstPromiseOnboardingState,
        permissionGranted: Boolean,
    ): FirstPromiseStateMutation {
        val attempt = state.usagePermissionAttempt
        if (attempt?.launchState != UsagePermissionLaunchState.Opened || attempt.terminalOutcome != null) {
            return FirstPromiseStateMutation.NoOp
        }
        return FirstPromiseStateMutation.Changed(
            state.copy(
                usagePermissionAttempt = if (permissionGranted) {
                    attempt.copy(terminalOutcome = UsagePermissionOutcome.Granted)
                } else {
                    attempt.copy(launchState = UsagePermissionLaunchState.UnresolvedAfterRecreation)
                },
            ),
        )
    }

    fun recoverAfterRecreation(state: FirstPromiseOnboardingState): FirstPromiseStateMutation =
        when (state.mappingValidity()) {
            MappingValidity.Invalid -> FirstPromiseStateMutation.Rejected
            MappingValidity.Complete -> if (state.phase in mappedRecoveryPhases) {
                FirstPromiseStateMutation.Changed(state.mappedRecoveryState())
            } else {
                FirstPromiseStateMutation.NoOp
            }
            MappingValidity.None -> FirstPromiseStateMutation.NoOp
        }

    fun applyEmergency(state: FirstPromiseOnboardingState): FirstPromiseEmergencyResult {
        when (state.mappingValidity()) {
            MappingValidity.Invalid -> return state.rejectedEmergencyResult()
            MappingValidity.Complete,
            MappingValidity.None,
            -> Unit
        }
        return when (state.phase) {
            FirstPromisePhase.GoalPending,
            FirstPromisePhase.UsageAccessPending,
            FirstPromisePhase.Analyzing,
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.AccessibilityPending,
            FirstPromisePhase.NotificationPending,
            -> FirstPromiseEmergencyResult(
                state = state.manualFallback(),
                action = FirstPromiseEmergencyAction.NavigateManualSelect,
            )

            FirstPromisePhase.ManualSelectPending -> FirstPromiseEmergencyResult(
                state = state,
                action = FirstPromiseEmergencyAction.Stay,
            )

            FirstPromisePhase.Persisting -> FirstPromiseEmergencyResult(
                state = state,
                action = FirstPromiseEmergencyAction.WaitForPersistence,
            )

            FirstPromisePhase.PersistFailed ->
                if (state.routineId == null) {
                    FirstPromiseEmergencyResult(
                        state = state.manualFallback(),
                        action = FirstPromiseEmergencyAction.NavigateManualSelect,
                    )
                } else {
                    FirstPromiseEmergencyResult(
                        state = state.copy(futureAnalysisDisabled = true),
                        action = FirstPromiseEmergencyAction.DisableFutureAnalysis,
                    )
                }

            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromisePhase.ResultEnabled,
            FirstPromisePhase.ResultDisabled,
            FirstPromisePhase.CompletedEnabled,
            FirstPromisePhase.CompletedDisabled,
            -> FirstPromiseEmergencyResult(
                state = state.copy(futureAnalysisDisabled = true),
                action = FirstPromiseEmergencyAction.DisableFutureAnalysis,
            )
        }
    }

    fun resolveEmergencyPersistence(
        state: FirstPromiseOnboardingState,
        resolution: FirstPromisePersistenceResolution,
    ): FirstPromiseEmergencyResult {
        if (state.phase != FirstPromisePhase.Persisting) {
            return state.rejectedEmergencyResult()
        }
        when (state.mappingValidity()) {
            MappingValidity.Invalid -> return state.rejectedEmergencyResult()
            MappingValidity.Complete -> return state.mappedRecoveryResult()
            MappingValidity.None -> Unit
        }
        return when (resolution) {
            is FirstPromisePersistenceResolution.Succeeded -> {
                if (resolution.routineId <= 0L) {
                    return state.rejectedEmergencyResult()
                }
                val resolvedRoutineId = state.routineId ?: resolution.routineId
                val resolvedScheduleState = if (state.routineId != null) {
                    state.scheduleState ?: resolution.scheduleState
                } else {
                    resolution.scheduleState
                }
                FirstPromiseEmergencyResult(
                    state = state.copy(
                        phase = if (resolvedScheduleState == FirstPromiseScheduleState.Enabled) {
                            FirstPromisePhase.ResultEnabled
                        } else {
                            FirstPromisePhase.SchedulePermissionRequired
                        },
                        routineId = resolvedRoutineId,
                        scheduleState = resolvedScheduleState,
                        pendingSystemAction = null,
                        analysisAttemptId = null,
                        futureAnalysisDisabled = true,
                    ),
                    action = FirstPromiseEmergencyAction.Stay,
                )
            }

            FirstPromisePersistenceResolution.Failed ->
                if (state.routineId == null) {
                    FirstPromiseEmergencyResult(
                        state = state.manualFallback(),
                        action = FirstPromiseEmergencyAction.NavigateManualSelect,
                    )
                } else {
                    FirstPromiseEmergencyResult(
                        state = state.copy(futureAnalysisDisabled = true),
                        action = FirstPromiseEmergencyAction.DisableFutureAnalysis,
                    )
                }
        }
    }

    fun acceptsAnalysisAttempt(
        state: FirstPromiseOnboardingState,
        attemptId: Long,
    ): Boolean =
        state.phase == FirstPromisePhase.Analyzing &&
            !state.futureAnalysisDisabled &&
            state.analysisAttemptId == attemptId

    private fun FirstPromiseOnboardingState.manualFallback(): FirstPromiseOnboardingState = copy(
        phase = FirstPromisePhase.ManualSelectPending,
        path = FirstPromisePath.Manual,
        draft = null,
        recommendationReasonRef = null,
        pendingSystemAction = null,
        analysisAttemptId = null,
        futureAnalysisDisabled = true,
    )

    private fun FirstPromiseOnboardingState.normalAnalysisFallback(): FirstPromiseOnboardingState = copy(
        phase = FirstPromisePhase.ManualSelectPending,
        path = FirstPromisePath.Manual,
        draft = null,
        recommendationReasonRef = null,
        pendingSystemAction = null,
        analysisAttemptId = null,
        futureAnalysisDisabled = false,
    )

    private fun FirstPromiseOnboardingState.mappingValidity(): MappingValidity = when {
        routineId == null && scheduleState == null -> MappingValidity.None
        routineId != null && routineId > 0L && scheduleState != null -> MappingValidity.Complete
        else -> MappingValidity.Invalid
    }

    private fun FirstPromiseOnboardingState.rejectedEmergencyResult() = FirstPromiseEmergencyResult(
        state = this,
        action = FirstPromiseEmergencyAction.Rejected,
    )

    private fun FirstPromiseOnboardingState.mappedRecoveryResult(): FirstPromiseEmergencyResult {
        return FirstPromiseEmergencyResult(
            state = mappedRecoveryState(),
            action = FirstPromiseEmergencyAction.DisableFutureAnalysis,
        )
    }

    private fun FirstPromiseOnboardingState.mappedRecoveryState(): FirstPromiseOnboardingState {
        val mappedScheduleState = checkNotNull(scheduleState)
        return copy(
            phase = if (mappedScheduleState == FirstPromiseScheduleState.Enabled) {
                FirstPromisePhase.ResultEnabled
            } else {
                FirstPromisePhase.SchedulePermissionRequired
            },
            pendingSystemAction = null,
            analysisAttemptId = null,
            futureAnalysisDisabled = true,
        )
    }

    private fun draftAndReasonAreValidForState(
        state: FirstPromiseOnboardingState,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): Boolean =
        FirstPromiseDraftInvariant.isValidForState(draft, state.goal, state.path) &&
            reason.selectedStartMinutes == draft.startMinutes &&
            FirstPromiseRecommendationPolicy.isValidReasonRef(draft.source, reason)

    private fun transitionWithDraft(
        state: FirstPromiseOnboardingState,
        target: FirstPromisePhase,
    ): FirstPromiseStateMutation =
        if (
            state.draft != null &&
            state.recommendationReasonRef != null &&
            draftAndReasonAreValidForState(state, state.draft, state.recommendationReasonRef)
        ) {
            transition(state, target)
        } else {
            FirstPromiseStateMutation.Rejected
        }
}
