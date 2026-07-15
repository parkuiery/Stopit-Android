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
            listOf(null, 77L).forEach { routineId ->
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
        assertEquals(FirstPromisePhase.Persisting, mappedFailure.state.phase)
        assertEquals(99L, mappedFailure.state.routineId)
        assertEquals(FirstPromiseScheduleState.Enabled, mappedFailure.state.scheduleState)
        assertTrue(mappedFailure.state.futureAnalysisDisabled)
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
