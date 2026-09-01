package com.uiery.keep.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.BlockAnalyticsCoordinator
import com.uiery.keep.analytics.BlockAnalyticsRequest
import com.uiery.keep.analytics.BlockDirectAnalyticsDelivery
import com.uiery.keep.analytics.FirstCoreActionDeliveryCoordinator
import com.uiery.keep.analytics.FirstCoreActionMarker
import com.uiery.keep.analytics.FirstCoreActionMarkerState
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.data.firstpromise.FirstPromiseCreationResult
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceCoordinator
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceResult
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.data.routine.RoutineRestoreAftercare
import com.uiery.keep.data.firstpromise.FirstPromiseAppCategoryBucket
import com.uiery.keep.data.firstpromise.FirstPromiseAttribution
import com.uiery.keep.data.firstpromise.FirstPromiseAttributionStore
import com.uiery.keep.data.firstpromise.FirstPromiseBlockSource
import com.uiery.keep.data.firstpromise.FirstPromiseCoreActionKind
import com.uiery.keep.data.firstpromise.FirstPromiseOutboxDispatcher
import com.uiery.keep.data.firstpromise.FirstPromiseValueEventInput
import com.uiery.keep.data.firstpromise.FirstPromiseValueReservation
import com.uiery.keep.datastore.BackupRestoreDataStoreKeyPolicy
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
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
import com.uiery.keep.domain.usageinsight.InsufficientReason
import com.uiery.keep.domain.usageinsight.OnboardingUsageAggregate
import com.uiery.keep.domain.usageinsight.OnboardingUsageInterval
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfilePolicy
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryDestination
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryRoutePolicy
import com.uiery.keep.feature.onboarding.entry.OnboardingEntrySideEffect
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryViewModel
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentConfig
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentResolution
import com.uiery.keep.feature.onboarding.intro.IntroSideEffect
import com.uiery.keep.feature.onboarding.intro.IntroViewModel
import com.uiery.keep.feature.onboarding.notification.NotificationSettingViewModel
import com.uiery.keep.feature.onboarding.permission.PermissionSettingViewModel
import com.uiery.keep.feature.onboarding.select.SelectAppViewModel
import com.uiery.keep.feature.splash.SplashSideEffect
import com.uiery.keep.feature.splash.SplashViewModel
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduler
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromiseCoachOnboardingIntegrationTest {
    @Test
    fun controlKeepsProductionIntroPermissionNotificationSelectionAndHomeCompletion() = runBlocking {
        assertEquals(OnboardingVariant.Control, OnboardingExperimentResolution().variant)
        assertEquals(OnboardingVariant.PromiseCoachV1, treatmentResolution().variant)

        val dataStore = InMemoryPreferencesDataStore(
            mutablePreferencesOf(PreferencesKey.IS_NEW to true),
        )
        val draftStore = FirstPromiseDraftStore(dataStore)
        val analytics = RecordingOnboardingAnalytics()
        val entryEffect = onboardingEntryViewModel(dataStore).resolveEntry()

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            entryEffect,
        )
        assertEquals(OnboardingVariant.Control, draftStore.readState().assignment)

        val introViewModel = IntroViewModel(analytics, draftStore)
        val introNavigation = async(start = CoroutineStart.UNDISPATCHED) {
            introViewModel.container.sideEffectFlow.first()
        }
        introViewModel.onStepViewed()
        introViewModel.onContinue()
        assertEquals(IntroSideEffect.NavigatePermissionSetting, introNavigation.await())
        PermissionSettingViewModel(analytics).apply {
            onStepViewed()
            onPermissionSettingsOpened()
            onPermissionGranted()
        }
        NotificationSettingViewModel(analytics).apply {
            onStepViewed()
            onPermissionDeniedAndContinue()
        }
        SelectAppViewModel(BlockingStateStore(dataStore), analytics).apply {
            onStepViewed()
            selectCategoryComplete(setOf("com.example.legacy"))
        }
        awaitCondition { BlockingStateStore(dataStore).readIsNew(default = true).not() }

        assertEquals(setOf("com.example.legacy"), BlockingStateStore(dataStore).readSelectedAppPackages())
        assertFalse(BlockingStateStore(dataStore).readIsNew())
        assertEquals(OnboardingVariant.Control, draftStore.readState().assignment)
        assertEquals(FirstPromisePhase.GoalPending, draftStore.readState().phase)
        assertNull(draftStore.readState().draft)
        assertNull(draftStore.readState().pendingSystemAction)

        val splash = SplashViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            firstPromiseDraftStore = draftStore,
            analytics = analytics,
            routineRestoreAftercare = emptyRoutineRestoreAftercare(dataStore),
            // 집중 세션이 없는 온보딩 경로다. 세션이 있으면 스플래시가 세션 화면으로 보낸다.
            pomodoroBlockContextSource = { null },
        )
        assertEquals(
            SplashSideEffect.MoveToHome,
            withTimeout(2_000L) { splash.container.sideEffectFlow.first() },
        )
        assertEquals(
            listOf(
                OnboardingStepName.INTRO,
                OnboardingStepName.PERMISSION,
                OnboardingStepName.NOTIFICATION,
                OnboardingStepName.SELECT_APP,
            ),
            analytics.completedSteps,
        )
        assertTrue(
            analytics.completedSteps.none {
                it in setOf(
                    OnboardingStepName.GOAL_SELECT,
                    OnboardingStepName.USAGE_ACCESS,
                    OnboardingStepName.PROMISE_PROPOSAL,
                    OnboardingStepName.PROMISE_RESULT,
                )
            },
        )
    }

    @Test
    fun treatmentGrantAccessibilityPreGrantAndNotificationDenialStillCompleteEnabled() = runBlocking {
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
        val dataStore = InMemoryPreferencesDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
            ),
        )
        val store = FirstPromiseDraftStore(dataStore)
        val analytics = RecordingOnboardingAnalytics()
        var loadedStartMinutes: Int? = null
        var accessibilityNavigationCount = 0
        PermissionSettingViewModel(
            analytics,
            store,
            Dispatchers.Unconfined,
            Dispatchers.Unconfined,
        ).loadFirstPromise(
            accessibilityGranted = true,
            onLoaded = { loadedStartMinutes = it },
            onNavigateNotification = { accessibilityNavigationCount++ },
        )
        awaitCondition { store.readState().phase == FirstPromisePhase.NotificationPending }

        assertEquals(draft.startMinutes, loadedStartMinutes)
        assertEquals(1, accessibilityNavigationCount)
        assertNull(store.readState().pendingSystemAction)

        var persistenceNavigationCount = 0
        val persistence = RecordingPersistenceCoordinator(store)
        NotificationSettingViewModel(
            analytics,
            store,
            Dispatchers.Unconfined,
            Dispatchers.Unconfined,
            persistence,
        ).onFirstPromisePermissionResult(
            granted = false,
            onNavigatePersistence = { persistenceNavigationCount++ },
        )
        awaitCondition { store.readState().phase == FirstPromisePhase.ResultEnabled }

        assertEquals(1, persistenceNavigationCount)
        assertEquals(1, persistence.persistCalls)
        assertEquals(
            listOf(AnalyticsOutcome.DENIED),
            analytics.permissionOutcomes
                .filter { it.first == AnalyticsPermissionName.NOTIFICATIONS }
                .map { it.second },
        )
        assertTrue(OnboardingStepName.NOTIFICATION in analytics.completedSteps)
        assertEquals(
            OnboardingEntryDestination.PromiseResult,
            OnboardingEntryRoutePolicy.destinationFor(store.readState().phase),
        )

        assertTrue(store.completeOnboarding() is FirstPromiseStateMutation.Changed)

        assertEquals(FirstPromisePhase.CompletedEnabled, store.readState().phase)
        assertEquals(OnboardingEntryDestination.Home, OnboardingEntryRoutePolicy.destinationFor(store.readState().phase))
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
    fun resetOnlyPolicyClearsSeededPromiseStateBeforeCleanControlReassignment() = runBlocking {
        val seededState = draftReadyState().copy(
            phase = FirstPromisePhase.SchedulePermissionRequired,
            routineId = 91L,
            scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            pendingSystemAction = PendingSystemAction.ExactAlarm,
            trackedMilestones = setOf(FirstPromiseMilestone.PromiseResultView),
        )
        val dataStore = InMemoryPreferencesDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(seededState),
                PreferencesKey.IS_NEW to false,
                PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED to true,
            ),
        )
        val seededStore = FirstPromiseDraftStore(dataStore)

        assertEquals(OnboardingVariant.PromiseCoachV1, seededStore.readState().assignment)
        assertEquals(draft, seededStore.readState().draft)
        assertEquals(PendingSystemAction.ExactAlarm, seededStore.readState().pendingSystemAction)
        assertEquals(91L, seededStore.readState().routineId)
        assertFalse(BlockingStateStore(dataStore).readIsNew())

        dataStore.updateData { preferences ->
            (preferences as MutablePreferences).apply {
                BackupRestoreDataStoreKeyPolicy.resetOnlyKeys.forEach { key -> remove(key) }
            }
        }

        val recreatedStore = FirstPromiseDraftStore(dataStore)
        val reset = recreatedStore.readState()
        assertNull(reset.assignment)
        assertEquals(FirstPromisePhase.GoalPending, reset.phase)
        assertNull(reset.draft)
        assertNull(reset.pendingSystemAction)
        assertNull(reset.routineId)
        assertTrue(reset.trackedMilestones.isEmpty())
        assertTrue(reset.pendingOnboardingAnalyticsEvents.isEmpty())
        assertTrue(BlockingStateStore(dataStore).readIsNew(default = true))
        assertNull(dataStore.current()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
        assertNull(dataStore.current()[PreferencesKey.IS_NEW])
        assertNull(dataStore.current()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])

        assertEquals(
            OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro),
            onboardingEntryViewModel(dataStore).resolveEntry(),
        )
        assertEquals(OnboardingVariant.Control, recreatedStore.readState().assignment)
        assertNull(recreatedStore.readState().draft)
        assertNull(recreatedStore.readState().pendingSystemAction)
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

        val attributionStore = RecordingAttributionStore(
            expectedDraftId = draft.draftId,
            expectedPackageName = draft.packageName,
        )
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

    private fun onboardingEntryViewModel(
        dataStore: DataStore<Preferences>,
    ) = OnboardingEntryViewModel(
        draftStore = FirstPromiseDraftStore(dataStore),
        experimentConfig = object : OnboardingExperimentConfig {
            override suspend fun resolve() = OnboardingExperimentResolution()
        },
    )

    private fun emptyRoutineRestoreAftercare(
        dataStore: DataStore<Preferences>,
    ) = RoutineRestoreAftercare(
        routineRepository = EmptyRoutineRepository,
        dataStore = dataStore,
        exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(
            RoutineScheduler(InstrumentationRegistry.getInstrumentation().targetContext),
        ),
        routineNoticeStore = RoutineNoticeStore(dataStore),
    )

    private suspend fun awaitCondition(condition: suspend () -> Boolean) {
        withTimeout(2_000L) {
            while (!condition()) delay(10L)
        }
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

        fun treatmentResolution() = OnboardingExperimentResolution(
            variant = OnboardingVariant.PromiseCoachV1,
            remoteReadable = true,
        )
    }
}

private class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }

    fun current(): Preferences = state.value
}

private class RecordingOnboardingAnalytics : KeepAnalytics {
    val completedSteps = mutableListOf<String>()
    val permissionOutcomes = mutableListOf<Pair<String, String>>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) {
        completedSteps += stepName
    }

    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) {
        permissionOutcomes += permissionName to outcome
    }

    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

private class RecordingPersistenceCoordinator(
    private val store: FirstPromiseDraftStore,
) : FirstPromisePersistenceCoordinator {
    var persistCalls = 0

    override suspend fun persistCurrentDraft(): FirstPromisePersistenceResult {
        persistCalls++
        val creation = FirstPromiseCreationResult(
            routineId = 73L,
            routine = RoutineModel(
                id = 73L,
                name = "Promise",
                startTime = LocalTime(21, 0),
                endTime = LocalTime(21, 30),
                repeatDays = "1111111",
                lockApplications = listOf("com.example.video"),
                isEnabled = true,
            ),
            scheduleState = FirstPromiseScheduleState.Enabled,
            schedulingSucceeded = true,
            created = true,
        )
        store.recordPersistenceMapping(creation.routineId, creation.scheduleState)
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? = null

    override suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult =
        FirstPromisePersistenceResult.MissingRoutine

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult =
        FirstPromisePersistenceResult.MissingRoutine
}

private object EmptyRoutineRepository : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(emptyList())
    override suspend fun fetchAllOnce(): List<RoutineModel> = emptyList()
}

private class RecordingAttributionStore(
    private val expectedDraftId: String,
    private val expectedPackageName: String,
) : FirstPromiseAttributionStore {
    var reservedAttribution: FirstPromiseAttribution? = null
    var reservedInput: FirstPromiseValueEventInput? = null

    override suspend fun findRoutineAttribution(routineId: Long) = null

    override suspend fun findDraftAttribution(
        draftId: String,
        origin: FirstPromiseOrigin,
    ) = FirstPromiseAttribution(draftId, origin, createdAtMillis = 100L)

    override suspend fun matchesDraftPackage(draftId: String, packageName: String): Boolean =
        draftId == expectedDraftId && packageName == expectedPackageName

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
