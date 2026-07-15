package com.uiery.keep.domain.firstpromise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromiseStatePolicyTest {

    @Test
    fun normalTransitionTableAcceptsOnlyTheDocumentedPhasePairs() {
        val allowed = mapOf(
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

        FirstPromisePhase.entries.forEach { from ->
            FirstPromisePhase.entries.forEach { to ->
                val actual = FirstPromiseStatePolicy.transition(
                    state = FirstPromiseOnboardingState(phase = from),
                    target = to,
                )
                val expectedAllowed = to in allowed.getValue(from)

                assertEquals("$from -> $to", expectedAllowed, actual !is FirstPromiseStateMutation.Rejected)
                if (from == to && expectedAllowed) {
                    assertEquals("$from self transition", FirstPromiseStateMutation.NoOp, actual)
                }
            }
        }

        assertEquals(FirstPromisePhase.entries.toSet(), allowed.keys)

        assertEquals(
            FirstPromiseStateMutation.Rejected,
            FirstPromiseStatePolicy.beginUsageAnalysis(
                state = populatedState(FirstPromisePhase.ManualSelectPending).copy(
                    futureAnalysisDisabled = false,
                    analysisAttemptId = null,
                ),
                attemptId = 10L,
            ),
        )
    }

    @Test
    fun acceptedForwardTransitionChangesOnlyThePhase() {
        val state = FirstPromiseOnboardingState(
            assignment = OnboardingVariant.PromiseCoachV1,
            assignmentVersion = OnboardingAssignmentVersion.V1,
            trackedMilestones = setOf(FirstPromiseMilestone.Exposure),
            phase = FirstPromisePhase.GoalPending,
            path = FirstPromisePath.Personalized,
            goal = FirstPromiseGoal.Sleep,
        )

        val mutation = FirstPromiseStatePolicy.transition(state, FirstPromisePhase.UsageAccessPending)

        assertTrue(mutation is FirstPromiseStateMutation.Changed)
        assertEquals(
            state.copy(phase = FirstPromisePhase.UsageAccessPending),
            (mutation as FirstPromiseStateMutation.Changed).state,
        )
    }

    @Test
    fun emergencyTransitionMatrixPreservesIdentityAndNeverCreatesOrDeletesAMapping() {
        val manualFallbackPhases = setOf(
            FirstPromisePhase.GoalPending,
            FirstPromisePhase.UsageAccessPending,
            FirstPromisePhase.Analyzing,
            FirstPromisePhase.DraftReady,
            FirstPromisePhase.AccessibilityPending,
            FirstPromisePhase.NotificationPending,
        )
        val disableFutureAnalysisPhases = setOf(
            FirstPromisePhase.SchedulePermissionRequired,
            FirstPromisePhase.ResultEnabled,
            FirstPromisePhase.ResultDisabled,
            FirstPromisePhase.CompletedEnabled,
            FirstPromisePhase.CompletedDisabled,
        )

        FirstPromisePhase.entries.forEach { phase ->
            listOf<Long?>(null).forEach { routineId ->
                val state = populatedState(phase = phase, routineId = routineId)

                val result = FirstPromiseStatePolicy.applyEmergency(state)

                assertEquals("$phase assignment", state.assignment, result.state.assignment)
                assertEquals("$phase version", state.assignmentVersion, result.state.assignmentVersion)
                assertEquals("$phase milestones", state.trackedMilestones, result.state.trackedMilestones)
                assertEquals("$phase goal", state.goal, result.state.goal)
                assertEquals("$phase mapping", routineId, result.state.routineId)
                assertEquals("$phase schedule", state.scheduleState, result.state.scheduleState)

                when (phase) {
                    in manualFallbackPhases -> {
                        assertEquals(FirstPromiseEmergencyAction.NavigateManualSelect, result.action)
                        assertEquals(FirstPromisePhase.ManualSelectPending, result.state.phase)
                        assertEquals(FirstPromisePath.Manual, result.state.path)
                        assertNull(result.state.draft)
                        assertNull(result.state.recommendationReasonRef)
                        assertNull(result.state.pendingSystemAction)
                        assertNull(result.state.analysisAttemptId)
                        assertTrue(result.state.futureAnalysisDisabled)
                    }

                    FirstPromisePhase.ManualSelectPending -> {
                        assertEquals(FirstPromiseEmergencyAction.Stay, result.action)
                        assertEquals(state, result.state)
                    }

                    FirstPromisePhase.Persisting -> {
                        assertEquals(FirstPromiseEmergencyAction.WaitForPersistence, result.action)
                        assertFalse(result.navigationAllowed)
                        assertEquals(state, result.state)
                    }

                    FirstPromisePhase.PersistFailed -> {
                        if (routineId == null) {
                            assertEquals(FirstPromiseEmergencyAction.NavigateManualSelect, result.action)
                            assertEquals(FirstPromisePhase.ManualSelectPending, result.state.phase)
                            assertNull(result.state.draft)
                            assertNull(result.state.recommendationReasonRef)
                            assertNull(result.state.pendingSystemAction)
                        } else {
                            assertEquals(FirstPromiseEmergencyAction.DisableFutureAnalysis, result.action)
                            assertEquals(state.copy(futureAnalysisDisabled = true), result.state)
                        }
                    }

                    in disableFutureAnalysisPhases -> {
                        assertEquals(FirstPromiseEmergencyAction.DisableFutureAnalysis, result.action)
                        assertEquals(state.copy(futureAnalysisDisabled = true), result.state)
                    }

                    else -> error("Unclassified phase $phase")
                }
            }
        }
    }

    @Test
    fun persistenceResolutionAfterEmergencyWaitUsesMappingOrFallsBackToManual() {
        val persisting = populatedState(FirstPromisePhase.Persisting, routineId = null)

        val enabled = FirstPromiseStatePolicy.resolveEmergencyPersistence(
            persisting,
            FirstPromisePersistenceResolution.Succeeded(
                routineId = 31L,
                scheduleState = FirstPromiseScheduleState.Enabled,
            ),
        )
        val disabled = FirstPromiseStatePolicy.resolveEmergencyPersistence(
            persisting,
            FirstPromisePersistenceResolution.Succeeded(
                routineId = 32L,
                scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            ),
        )
        val failed = FirstPromiseStatePolicy.resolveEmergencyPersistence(
            persisting,
            FirstPromisePersistenceResolution.Failed,
        )

        assertEquals(FirstPromisePhase.ResultEnabled, enabled.state.phase)
        assertEquals(31L, enabled.state.routineId)
        assertEquals(FirstPromiseEmergencyAction.Stay, enabled.action)
        assertEquals(FirstPromisePhase.SchedulePermissionRequired, disabled.state.phase)
        assertEquals(32L, disabled.state.routineId)
        assertEquals(FirstPromisePhase.ManualSelectPending, failed.state.phase)
        assertEquals(FirstPromiseEmergencyAction.NavigateManualSelect, failed.action)
        assertNull(failed.state.draft)
        assertNull(failed.state.recommendationReasonRef)
        assertNull(failed.state.pendingSystemAction)

        val alreadyMapped = FirstPromiseStatePolicy.resolveEmergencyPersistence(
            persisting.copy(routineId = 99L, scheduleState = FirstPromiseScheduleState.Enabled),
            FirstPromisePersistenceResolution.Succeeded(
                routineId = 100L,
                scheduleState = FirstPromiseScheduleState.DisabledUnknown,
            ),
        )
        assertEquals(99L, alreadyMapped.state.routineId)
        assertEquals(FirstPromiseScheduleState.Enabled, alreadyMapped.state.scheduleState)

        val mappedFailure = FirstPromiseStatePolicy.resolveEmergencyPersistence(
            persisting.copy(routineId = 99L, scheduleState = FirstPromiseScheduleState.Enabled),
            FirstPromisePersistenceResolution.Failed,
        )
        assertEquals(FirstPromiseEmergencyAction.DisableFutureAnalysis, mappedFailure.action)
        assertEquals(FirstPromisePhase.ResultEnabled, mappedFailure.state.phase)
        assertEquals(99L, mappedFailure.state.routineId)
        assertEquals(FirstPromiseScheduleState.Enabled, mappedFailure.state.scheduleState)
        assertTrue(mappedFailure.state.futureAnalysisDisabled)
    }

    @Test
    fun completeMappingsInPreResultPhasesRecoverOnlyThroughExplicitRecreationCommand() {
        val preResultPhases = setOf(
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

        preResultPhases.forEach { phase ->
            val enabled = populatedState(phase, routineId = 77L)
            val enabledMutation = FirstPromiseStatePolicy.recoverAfterRecreation(enabled)
            assertTrue("$phase enabled mutation", enabledMutation is FirstPromiseStateMutation.Changed)
            val enabledState = (enabledMutation as FirstPromiseStateMutation.Changed).state
            assertEquals("$phase enabled recovery", FirstPromisePhase.ResultEnabled, enabledState.phase)
            assertEquals(77L, enabledState.routineId)

            val disabled = enabled.copy(scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing)
            val disabledMutation = FirstPromiseStatePolicy.recoverAfterRecreation(disabled)
            assertTrue("$phase disabled mutation", disabledMutation is FirstPromiseStateMutation.Changed)
            val disabledState = (disabledMutation as FirstPromiseStateMutation.Changed).state
            assertEquals(
                "$phase disabled recovery",
                FirstPromisePhase.SchedulePermissionRequired,
                disabledState.phase,
            )
            assertEquals(77L, disabledState.routineId)
        }
    }

    @Test
    fun emergencyMatrixIsNotOverriddenByACompleteMapping() {
        val mappedGoal = populatedState(FirstPromisePhase.GoalPending, routineId = 77L)
        val mappedManual = populatedState(FirstPromisePhase.ManualSelectPending, routineId = 77L)
        val mappedPersisting = populatedState(FirstPromisePhase.Persisting, routineId = 77L)

        val goalResult = FirstPromiseStatePolicy.applyEmergency(mappedGoal)
        assertEquals(FirstPromiseEmergencyAction.NavigateManualSelect, goalResult.action)
        assertEquals(FirstPromisePhase.ManualSelectPending, goalResult.state.phase)
        assertEquals(77L, goalResult.state.routineId)

        val manualResult = FirstPromiseStatePolicy.applyEmergency(mappedManual)
        assertEquals(FirstPromiseEmergencyAction.Stay, manualResult.action)
        assertEquals(mappedManual, manualResult.state)

        val persistingResult = FirstPromiseStatePolicy.applyEmergency(mappedPersisting)
        assertEquals(FirstPromiseEmergencyAction.WaitForPersistence, persistingResult.action)
        assertFalse(persistingResult.navigationAllowed)
        assertEquals(mappedPersisting, persistingResult.state)
    }

    @Test
    fun partialOrInvalidMappingsAreRejectedWithoutNavigationOrMutation() {
        val missingSchedule = populatedState(FirstPromisePhase.GoalPending, routineId = 77L).copy(scheduleState = null)
        val missingRoutine = populatedState(FirstPromisePhase.GoalPending).copy(
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val invalidRoutine = populatedState(FirstPromisePhase.GoalPending, routineId = 0L)

        listOf(missingSchedule, missingRoutine, invalidRoutine).forEach { state ->
            val result = FirstPromiseStatePolicy.applyEmergency(state)
            assertEquals(FirstPromiseEmergencyAction.Rejected, result.action)
            assertFalse(result.navigationAllowed)
            assertEquals(state, result.state)
        }
    }

    @Test
    fun invalidatedAndStaleAnalysisAttemptsCannotWriteStateOrAnalytics() {
        val analyzing = populatedState(FirstPromisePhase.Analyzing).copy(analysisAttemptId = 9L)

        assertTrue(FirstPromiseStatePolicy.acceptsAnalysisAttempt(analyzing, attemptId = 9L))
        assertFalse(FirstPromiseStatePolicy.acceptsAnalysisAttempt(analyzing, attemptId = 8L))

        val emergency = FirstPromiseStatePolicy.applyEmergency(analyzing)

        assertFalse(FirstPromiseStatePolicy.acceptsAnalysisAttempt(emergency.state, attemptId = 9L))
    }

    private fun populatedState(
        phase: FirstPromisePhase,
        routineId: Long? = null,
    ) = FirstPromiseOnboardingState(
        assignment = OnboardingVariant.PromiseCoachV1,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        trackedMilestones = setOf(FirstPromiseMilestone.Exposure),
        phase = phase,
        path = FirstPromisePath.Personalized,
        goal = FirstPromiseGoal.Sleep,
        draft = FirstPromiseDraft(
            draftId = "local-draft",
            goal = FirstPromiseGoal.Sleep,
            packageName = "com.example.video",
            appLabel = "Video",
            startMinutes = 22 * 60,
            repeatDays = setOf(1, 3, 5),
            source = FirstPromiseSource.Personalized,
        ),
        recommendationReasonRef = RecommendationReasonRef(
            patternType = UsagePatternType.Night,
            usageCoverageDays = 7,
            eventCoverageDays = 6,
            isGoalDefault = false,
            selectedStartMinutes = 22 * 60,
        ),
        routineId = routineId,
        scheduleState = routineId?.let { FirstPromiseScheduleState.Enabled },
        pendingSystemAction = PendingSystemAction.UsageAccess,
        analysisAttemptId = 9L,
    )
}
