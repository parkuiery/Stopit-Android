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
}

data class FirstPromiseEmergencyResult(
    val state: FirstPromiseOnboardingState,
    val action: FirstPromiseEmergencyAction,
) {
    val navigationAllowed: Boolean = action != FirstPromiseEmergencyAction.WaitForPersistence
}

sealed interface FirstPromisePersistenceResolution {
    data class Succeeded(
        val routineId: Long,
        val scheduleState: FirstPromiseScheduleState,
    ) : FirstPromisePersistenceResolution

    data object Failed : FirstPromisePersistenceResolution
}

object FirstPromiseStatePolicy {
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

    fun storeDraft(
        state: FirstPromiseOnboardingState,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation {
        if (!draftMatchesState(state, draft, reason)) {
            return FirstPromiseStateMutation.Rejected
        }
        val phaseMutation = transition(state, FirstPromisePhase.DraftReady)
        if (phaseMutation is FirstPromiseStateMutation.Rejected) {
            return phaseMutation
        }
        val phaseState = (phaseMutation as? FirstPromiseStateMutation.Changed)?.state ?: state
        val nextState = phaseState.copy(draft = draft, recommendationReasonRef = reason)
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
        if (!acceptsAnalysisAttempt(state, attemptId) || !draftMatchesState(state, draft, reason)) {
            return FirstPromiseStateMutation.Rejected
        }
        val phaseMutation = transition(state, FirstPromisePhase.DraftReady)
        val phaseState = (phaseMutation as? FirstPromiseStateMutation.Changed)?.state
            ?: return FirstPromiseStateMutation.Rejected
        return FirstPromiseStateMutation.Changed(
            phaseState.copy(draft = draft, recommendationReasonRef = reason),
        )
    }

    fun recordPersistenceMapping(
        state: FirstPromiseOnboardingState,
        routineId: Long,
        scheduleState: FirstPromiseScheduleState,
    ): FirstPromiseStateMutation {
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

    fun applyEmergency(state: FirstPromiseOnboardingState): FirstPromiseEmergencyResult {
        return when (state.phase) {
            FirstPromisePhase.GoalPending,
            FirstPromisePhase.UsageAccessPending,
            FirstPromisePhase.Analyzing,
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.AccessibilityPending,
            FirstPromisePhase.NotificationPending,
            FirstPromisePhase.PersistFailed,
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
        require(state.phase == FirstPromisePhase.Persisting) {
            "Emergency persistence can only resolve from Persisting"
        }
        return when (resolution) {
            is FirstPromisePersistenceResolution.Succeeded -> {
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

            FirstPromisePersistenceResolution.Failed -> FirstPromiseEmergencyResult(
                state = state.manualFallback(),
                action = FirstPromiseEmergencyAction.NavigateManualSelect,
            )
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

    private fun draftMatchesState(
        state: FirstPromiseOnboardingState,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): Boolean =
        draft.goal == state.goal && reason.selectedStartMinutes == draft.startMinutes
}
