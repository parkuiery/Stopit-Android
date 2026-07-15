package com.uiery.keep.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.BlockAnalyticsCoordinator
import com.uiery.keep.analytics.BlockAnalyticsRequest
import com.uiery.keep.analytics.BlockDirectAnalyticsDelivery
import com.uiery.keep.analytics.FirstCoreActionDeliveryCoordinator
import com.uiery.keep.analytics.FirstCoreActionMarker
import com.uiery.keep.analytics.FirstCoreActionMarkerState
import com.uiery.keep.data.firstpromise.FirstPromiseAppCategoryBucket
import com.uiery.keep.data.firstpromise.FirstPromiseAttribution
import com.uiery.keep.data.firstpromise.FirstPromiseAttributionStore
import com.uiery.keep.data.firstpromise.FirstPromiseBlockSource
import com.uiery.keep.data.firstpromise.FirstPromiseCoreActionKind
import com.uiery.keep.data.firstpromise.FirstPromiseOutboxDispatcher
import com.uiery.keep.data.firstpromise.FirstPromiseValueEventInput
import com.uiery.keep.data.firstpromise.FirstPromiseValueReservation
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.FirstPromiseStatePolicy
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.domain.firstpromise.UsagePermissionLaunchState
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import com.uiery.keep.domain.firstpromise.UsagePermissionAttempt
import com.uiery.keep.domain.usageinsight.InsufficientReason
import com.uiery.keep.domain.usageinsight.OnboardingUsageAggregate
import com.uiery.keep.domain.usageinsight.OnboardingUsageInterval
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfilePolicy
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryDestination
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryRoutePolicy
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentPolicy
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentSnapshot
import com.uiery.keep.feature.onboarding.notification.PostNotificationPermissionResultAction
import com.uiery.keep.feature.onboarding.notification.resolvePostNotificationPermissionResultAction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromiseCoachOnboardingIntegrationTest {
    @Test
    fun controlDefaultsRemainLegacyWhileOnlyReadableEnabledBucketsEnterTreatment() {
        assertEquals(
            OnboardingVariant.Control,
            OnboardingExperimentPolicy.assign(OnboardingExperimentSnapshot(), bucket = 0),
        )
        assertEquals(
            OnboardingVariant.Control,
            OnboardingExperimentPolicy.assign(
                OnboardingExperimentSnapshot(
                    treatmentPercent = 100,
                    newAssignmentEnabled = true,
                    remoteReadable = false,
                ),
                bucket = 0,
            ),
        )
        assertEquals(
            OnboardingVariant.PromiseCoachV1,
            OnboardingExperimentPolicy.assign(treatmentSnapshot(), bucket = 99),
        )
    }

    @Test
    fun treatmentGrantAccessibilityPreGrantAndNotificationDenialStillCompleteEnabled() {
        var state = treatmentGoalState()
        state = changed(FirstPromiseStatePolicy.beginUsagePermissionSettingsAttempt(state, 1L))
        state = changed(FirstPromiseStatePolicy.markUsagePermissionOpened(state, 1L))
        state = changed(
            FirstPromiseStatePolicy.recordUsagePermissionResume(
                state,
                attemptId = 1L,
                permissionGranted = true,
                sameProcess = true,
            ),
        )
        assertEquals(UsagePermissionOutcome.Granted, state.usagePermissionAttempt?.terminalOutcome)
        state = changed(FirstPromiseStatePolicy.completeUsageAccess(state))
        state = changed(FirstPromiseStatePolicy.beginUsageAnalysis(state, 2L))
        state = changed(FirstPromiseStatePolicy.completeAnalysis(state, 2L, draft, reason))
        state = changed(FirstPromiseStatePolicy.startFirstPromise(state))

        assertEquals(PendingSystemAction.Accessibility, state.pendingSystemAction)
        state = changed(FirstPromiseStatePolicy.requestNotification(state))
        assertEquals(FirstPromisePhase.NotificationPending, state.phase)
        assertNull(state.pendingSystemAction)
        assertEquals(
            PostNotificationPermissionResultAction.RecordDenialAndContinue,
            resolvePostNotificationPermissionResultAction(isGranted = false),
        )

        state = changed(FirstPromiseStatePolicy.beginPersistence(state))
        state = changed(
            FirstPromiseStatePolicy.recordPersistenceMapping(
                state,
                routineId = 10L,
                scheduleState = FirstPromiseScheduleState.Enabled,
            ),
        )
        state = changed(FirstPromiseStatePolicy.completeOnboarding(state))

        assertEquals(FirstPromisePhase.CompletedEnabled, state.phase)
        assertEquals(OnboardingEntryDestination.Home, OnboardingEntryRoutePolicy.destinationFor(state.phase))
    }

    @Test
    fun usageDenialOemSettingsFailureAndInsufficientDataAllReachManualFallback() {
        var denied = treatmentGoalState()
        denied = changed(FirstPromiseStatePolicy.beginUsagePermissionSettingsAttempt(denied, 1L))
        denied = changed(FirstPromiseStatePolicy.markUsagePermissionOpened(denied, 1L))
        denied = changed(
            FirstPromiseStatePolicy.recordUsagePermissionResume(
                denied,
                attemptId = 1L,
                permissionGranted = false,
                sameProcess = true,
            ),
        )
        denied = changed(FirstPromiseStatePolicy.chooseManualUsageAccess(denied, 2L))
        assertEquals(FirstPromisePhase.ManualSelectPending, denied.phase)
        assertEquals(FirstPromisePath.Manual, denied.path)
        assertEquals(UsagePermissionOutcome.Skipped, denied.usagePermissionAttempt?.terminalOutcome)

        var unavailable = treatmentGoalState()
        unavailable = changed(FirstPromiseStatePolicy.beginUsagePermissionSettingsAttempt(unavailable, 1L))
        unavailable = changed(FirstPromiseStatePolicy.markUsagePermissionLaunchFailed(unavailable, 1L))
        assertEquals(UsagePermissionOutcome.Unknown, unavailable.usagePermissionAttempt?.terminalOutcome)
        assertNull(unavailable.pendingSystemAction)
        unavailable = changed(FirstPromiseStatePolicy.chooseManualUsageAccess(unavailable, 2L))
        assertEquals(FirstPromisePhase.ManualSelectPending, unavailable.phase)

        val insufficient = OnboardingUsageProfilePolicy.evaluate(
            aggregates = listOf(
                OnboardingUsageAggregate("local.only", 60_000L, 1L, LocalDate.of(2026, 7, 16)),
            ),
            intervals = emptyList(),
            appLabels = mapOf("local.only" to "Local"),
            enabledRoutines = emptyList(),
            goalDefaultStartMinutes = 21 * 60,
            proposedRepeatDays = allDays,
            zoneId = ZoneId.of("Asia/Seoul"),
        ) as OnboardingUsageProfileResult.Insufficient
        assertEquals(InsufficientReason.InsufficientUsageCoverage, insufficient.reason)
    }

    @Test
    fun processRecreationPreservesEveryPendingSystemActionWithoutInventingPermissionResults() {
        var usage = treatmentGoalState()
        usage = changed(FirstPromiseStatePolicy.beginUsagePermissionSettingsAttempt(usage, 1L))
        usage = roundTrip(usage)
        usage = changed(
            FirstPromiseStatePolicy.reconcileUsagePermissionAfterRecreation(
                usage,
                permissionGranted = false,
            ),
        )
        assertEquals(UsagePermissionLaunchState.UnresolvedAfterRecreation, usage.usagePermissionAttempt?.launchState)
        assertNull(usage.usagePermissionAttempt?.terminalOutcome)
        assertNull(usage.pendingSystemAction)
        assertEquals(OnboardingEntryDestination.UsageAccess, OnboardingEntryRoutePolicy.destinationFor(usage.phase))

        val accessibility = roundTrip(
            draftReadyState().copy(
                phase = FirstPromisePhase.AccessibilityPending,
                pendingSystemAction = PendingSystemAction.Accessibility,
            ),
        )
        assertEquals(FirstPromiseStateMutation.NoOp, FirstPromiseStatePolicy.recoverAfterRecreation(accessibility))
        assertEquals(PendingSystemAction.Accessibility, accessibility.pendingSystemAction)
        assertEquals(
            OnboardingEntryDestination.PromiseAccessibility,
            OnboardingEntryRoutePolicy.destinationFor(accessibility.phase),
        )

        val exactAlarm = roundTrip(
            draftReadyState().copy(
                phase = FirstPromisePhase.SchedulePermissionRequired,
                routineId = 22L,
                scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
                pendingSystemAction = PendingSystemAction.ExactAlarm,
            ),
        )
        assertEquals(FirstPromiseStateMutation.NoOp, FirstPromiseStatePolicy.recoverAfterRecreation(exactAlarm))
        assertEquals(PendingSystemAction.ExactAlarm, exactAlarm.pendingSystemAction)
        assertEquals(OnboardingEntryDestination.PromiseResult, OnboardingEntryRoutePolicy.destinationFor(exactAlarm.phase))
    }

    @Test
    fun exactAlarmResolutionHasDistinctEnabledAndDisabledTerminalResults() {
        val persisting = changed(
            FirstPromiseStatePolicy.beginPersistence(
                draftReadyState().copy(phase = FirstPromisePhase.NotificationPending),
            ),
        )
        val permissionRequired = changed(
            FirstPromiseStatePolicy.recordPersistenceMapping(
                persisting,
                routineId = 31L,
                scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            ),
        )
        assertEquals(FirstPromisePhase.SchedulePermissionRequired, permissionRequired.phase)

        val disabledResult = changed(
            FirstPromiseStatePolicy.resolveScheduleState(
                permissionRequired,
                routineId = 31L,
                scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            ),
        )
        val disabledTerminal = changed(FirstPromiseStatePolicy.completeOnboarding(disabledResult))
        assertEquals(FirstPromisePhase.CompletedDisabled, disabledTerminal.phase)

        val enabledResult = changed(
            FirstPromiseStatePolicy.resolveScheduleState(
                permissionRequired,
                routineId = 31L,
                scheduleState = FirstPromiseScheduleState.Enabled,
            ),
        )
        val enabledTerminal = changed(FirstPromiseStatePolicy.completeOnboarding(enabledResult))
        assertEquals(FirstPromisePhase.CompletedEnabled, enabledTerminal.phase)
    }

    @Test
    fun midnightCrossingAndTimezoneReevaluationUseCalendarLocalBuckets() {
        val seoul = ZoneId.of("Asia/Seoul")
        val firstDay = LocalDate.of(2026, 7, 13)
        val localDates = (0..2).map { firstDay.plusDays(it.toLong()) }
        val midnight = (OnboardingUsageProfilePolicy.evaluate(
            aggregates = localDates.map { localDate ->
                OnboardingUsageAggregate(
                    packageName = "com.example.video",
                    totalForegroundMillis = Duration.ofMinutes(30).toMillis(),
                    lastUsedEpochMillis = localDate.atStartOfDay(seoul).plusMinutes(30).toInstant().toEpochMilli(),
                    localDate = localDate,
                )
            },
            intervals = localDates.map { localDate ->
                val localMidnight = localDate.atStartOfDay(seoul).toInstant()
                OnboardingUsageInterval(
                    packageName = "com.example.video",
                    startMillis = localMidnight.minus(Duration.ofMinutes(30)).toEpochMilli(),
                    endMillis = localMidnight.plus(Duration.ofMinutes(30)).toEpochMilli(),
                    localDate = localDate,
                )
            },
            appLabels = mapOf("com.example.video" to "Video"),
            enabledRoutines = emptyList(),
            goalDefaultStartMinutes = 21 * 60,
            proposedRepeatDays = allDays,
            zoneId = seoul,
        ) as OnboardingUsageProfileResult.Ready).profile
        assertEquals(0, midnight.suggestedStartMinutes)
        assertEquals(UsagePatternType.Night, midnight.patternType)

        val base = Instant.parse("2026-07-13T14:00:00Z")
        val instants = (0..2).map { base.plus(Duration.ofDays(it.toLong())) }
        val inSeoul = profile(seoul, instants, intervalMinutes = 30)
        val inLosAngeles = profile(ZoneId.of("America/Los_Angeles"), instants, intervalMinutes = 30)
        assertEquals(23 * 60, inSeoul.suggestedStartMinutes)
        assertEquals(7 * 60, inLosAngeles.suggestedStartMinutes)
    }

    @Test
    fun usageAccessRevocationAfterSetupFallsBackToInsufficientWithoutReopeningCompletedOnboarding() {
        val completed = draftReadyState().copy(
            phase = FirstPromisePhase.CompletedEnabled,
            draft = null,
            recommendationReasonRef = null,
            routineId = 41L,
            scheduleState = FirstPromiseScheduleState.Enabled,
        )
        val afterRecreation = FirstPromiseStatePolicy.recoverAfterRecreation(roundTrip(completed))
        assertEquals(FirstPromiseStateMutation.NoOp, afterRecreation)
        assertEquals(OnboardingEntryDestination.Home, OnboardingEntryRoutePolicy.destinationFor(completed.phase))

        val revokedProfile = OnboardingUsageProfilePolicy.evaluate(
            aggregates = emptyList(),
            intervals = emptyList(),
            appLabels = emptyMap(),
            enabledRoutines = emptyList(),
            goalDefaultStartMinutes = 21 * 60,
            proposedRepeatDays = allDays,
            zoneId = ZoneId.of("Asia/Seoul"),
        ) as OnboardingUsageProfileResult.Insufficient
        assertEquals(InsufficientReason.InsufficientUsageCoverage, revokedProfile.reason)
    }

    @Test
    fun cleanResetStateContainsNoAssignmentDraftPendingActionOrCompletionProof() {
        val reset = FirstPromiseOnboardingState()

        assertNull(reset.assignment)
        assertEquals(FirstPromisePhase.GoalPending, reset.phase)
        assertNull(reset.draft)
        assertNull(reset.pendingSystemAction)
        assertNull(reset.routineId)
        assertTrue(reset.trackedMilestones.isEmpty())
        assertTrue(reset.pendingOnboardingAnalyticsEvents.isEmpty())
    }

    @Test
    fun durablePracticeStartTokenAttributesActualTimedLockBlockToPractice() = runBlocking {
        val dataStore = InMemoryPreferencesDataStore()
        val practiceStore = FirstPromisePracticeStore(dataStore)
        val token = FirstPromisePracticeToken(
            draftId = draft.draftId,
            startedAtMillis = 100L,
            expiresAtMillis = 600_100L,
            attemptId = "attempt",
            encodedDeadline = "deadline",
        )
        practiceStore.saveStarted(token)
        val recreatedPracticeStore = FirstPromisePracticeStore(dataStore)
        assertEquals(token, recreatedPracticeStore.readActiveToken(200L))

        val attributionStore = RecordingAttributionStore()
        val coordinator = BlockAnalyticsCoordinator(
            attributionStore = attributionStore,
            activePracticeAt = recreatedPracticeStore::readActiveToken,
            firstCoreActionCoordinator = FirstCoreActionDeliveryCoordinator(
                reservationStore = { false },
                marker = InMemoryFirstCoreMarker,
            ),
            outboxDispatcher = ReadyOutboxDispatcher,
            directDelivery = NoDirectDelivery,
            nowMillis = { 200L },
        )

        val result = coordinator.track(
            BlockAnalyticsRequest(
                packageName = draft.packageName,
                blockSource = AnalyticsBlockSource.TIMED_LOCK,
                routineId = null,
                goalLockId = null,
            ),
        )

        assertTrue(result.showFirstCoreActionFeedback)
        assertEquals(FirstPromiseOrigin.FirstPromisePractice, attributionStore.reservedAttribution?.origin)
        assertEquals(FirstPromiseBlockSource.TimedLock, attributionStore.reservedInput?.blockSource)
        assertEquals(FirstPromiseAppCategoryBucket.Video, attributionStore.reservedInput?.categoryBucket)
    }

    private fun treatmentGoalState(): FirstPromiseOnboardingState = changed(
        FirstPromiseStatePolicy.choosePersonalizedGoal(
            FirstPromiseOnboardingState(
                assignment = OnboardingVariant.PromiseCoachV1,
                assignmentVersion = OnboardingAssignmentVersion.V1,
            ),
            FirstPromiseGoal.Focus,
        ),
    )

    private fun draftReadyState() = FirstPromiseOnboardingState(
        assignment = OnboardingVariant.PromiseCoachV1,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        phase = FirstPromisePhase.DraftReady,
        path = FirstPromisePath.Personalized,
        goal = FirstPromiseGoal.Focus,
        draft = draft,
        recommendationReasonRef = reason,
    )

    private fun profile(
        zone: ZoneId,
        instants: List<Instant>,
        intervalMinutes: Long,
    ) = (OnboardingUsageProfilePolicy.evaluate(
        aggregates = instants.map { instant ->
            OnboardingUsageAggregate(
                packageName = "com.example.video",
                totalForegroundMillis = Duration.ofMinutes(60).toMillis(),
                lastUsedEpochMillis = instant.toEpochMilli(),
                localDate = instant.atZone(zone).toLocalDate(),
            )
        },
        intervals = instants.map { instant ->
            OnboardingUsageInterval(
                packageName = "com.example.video",
                startMillis = instant.toEpochMilli(),
                endMillis = instant.plus(Duration.ofMinutes(intervalMinutes)).toEpochMilli(),
                localDate = instant.atZone(zone).toLocalDate(),
            )
        },
        appLabels = mapOf("com.example.video" to "Video"),
        enabledRoutines = emptyList(),
        goalDefaultStartMinutes = 21 * 60,
        proposedRepeatDays = allDays,
        zoneId = zone,
    ) as OnboardingUsageProfileResult.Ready).profile

    private fun changed(mutation: FirstPromiseStateMutation): FirstPromiseOnboardingState =
        (mutation as FirstPromiseStateMutation.Changed).state

    private fun roundTrip(state: FirstPromiseOnboardingState): FirstPromiseOnboardingState =
        Json.decodeFromString<FirstPromiseOnboardingState>(Json.encodeToString(state))

    private companion object {
        val allDays = (1..7).toSet()
        val draft = FirstPromiseDraft(
            draftId = "integration-draft",
            goal = FirstPromiseGoal.Focus,
            packageName = "com.example.video",
            appLabel = "Video",
            startMinutes = 21 * 60,
            repeatDays = allDays,
            source = FirstPromiseSource.Personalized,
        )
        val reason = RecommendationReasonRef(
            patternType = UsagePatternType.TopApp,
            usageCoverageDays = 3,
            eventCoverageDays = 0,
            isGoalDefault = true,
            selectedStartMinutes = draft.startMinutes,
        )

        fun treatmentSnapshot() = OnboardingExperimentSnapshot(
            treatmentPercent = 100,
            newAssignmentEnabled = true,
            remoteReadable = true,
        )
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class RecordingAttributionStore : FirstPromiseAttributionStore {
    var reservedAttribution: FirstPromiseAttribution? = null
    var reservedInput: FirstPromiseValueEventInput? = null

    override suspend fun findRoutineAttribution(routineId: Long) = null

    override suspend fun findDraftAttribution(
        draftId: String,
        origin: FirstPromiseOrigin,
    ) = FirstPromiseAttribution(draftId, origin, createdAtMillis = 100L)

    override suspend fun hasFirstCoreActionReservation() = false

    override suspend fun reserveValueEvents(
        attribution: FirstPromiseAttribution,
        input: FirstPromiseValueEventInput,
        allowFirst: Boolean,
    ): FirstPromiseValueReservation {
        reservedAttribution = attribution
        reservedInput = input
        return FirstPromiseValueReservation.Created(
            if (allowFirst) FirstPromiseCoreActionKind.First else FirstPromiseCoreActionKind.Repeat,
        )
    }
}

private object InMemoryFirstCoreMarker : FirstCoreActionMarker {
    override suspend fun read(nowMillis: Long) = FirstCoreActionMarkerState(
        firstOpenTimestampMillis = 0L,
        hasTracked = false,
    )

    override suspend fun mark(firstOpenTimestampMillis: Long) = Unit
}

private object ReadyOutboxDispatcher : FirstPromiseOutboxDispatcher {
    override suspend fun drainAll() = Unit
    override suspend fun drainDraft(draftId: String) = Unit
    override suspend fun cleanupSentRows() = Unit
    override suspend fun creationEventsSent(draftId: String) = true
    override suspend fun analyticsBarrierComplete(draftId: String) = true
}

private object NoDirectDelivery : BlockDirectAnalyticsDelivery {
    override fun appBlock(request: BlockAnalyticsRequest, origin: FirstPromiseOrigin?) = Unit

    override fun coreAction(
        request: BlockAnalyticsRequest,
        kind: FirstPromiseCoreActionKind,
        elapsedSeconds: Long,
        origin: FirstPromiseOrigin?,
    ) = Unit
}
