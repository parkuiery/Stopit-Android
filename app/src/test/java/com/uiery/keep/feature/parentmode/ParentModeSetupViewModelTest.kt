package com.uiery.keep.feature.parentmode

import com.uiery.keep.data.parentmode.ParentModeSessionStore
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentModeSetupViewModelTest {
    @Test
    fun initLogsParentModeSetupScreenView() {
        val analytics = ParentModeSetupRecordingAnalytics()

        createViewModel(analytics = analytics)

        assertEquals(listOf(KeepAnalyticsScreen.PARENT_MODE_SETUP), analytics.screenViews)
    }

    /**
     * The blocking selection is the list of apps the user wants stopped. Parent mode reads its list
     * as the only apps that stay open, so seeding one from the other hands the parent an allowlist
     * that locks everything they did not already block — the VOC that says parent mode locks every
     * app. The parent picks the allowed apps deliberately or starts with none.
     */
    @Test
    fun openingParentModeSetupLeavesAllowedAppsEmptyInsteadOfSeedingTheBlockingSelection() = runBlocking {
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.SELECTED_APP_PACKAGES] = setOf("com.video.app", "com.kids.app")
        }
        val store = ParentModeSessionStore(dataStore)
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 601_000L,
                durationMinutes = 10,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
        )
        val viewModel = createViewModel(sessionStore = store, nowMillis = { 120_000L })

        viewModel.refreshActiveSessionStatus()
        awaitUntil { viewModel.state.value.activeSession != null }

        assertEquals(emptySet<String>(), viewModel.state.value.allowedApps)
        assertEquals(10, viewModel.state.value.durationMinutes)
        assertTrue(viewModel.state.value.canAttemptStart.not())
    }

    @Test
    fun matchingGuardianPinEnablesStartAttemptWithoutExposingPinInSession() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { 1_000L },
        )
        viewModel.setAllowedApps(setOf("com.video.app"))

        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")

        assertTrue(viewModel.state.value.canAttemptStart)

        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        assertEquals(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 601_000L,
                durationMinutes = 10,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
            store.read(),
        )
    }

    @Test
    fun mismatchedGuardianPinBlocksStartAndKeepsSessionUnpersisted() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(sessionStore = store)
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("9999")

        assertTrue(viewModel.state.value.canAttemptStart.not())

        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.state.value.setupIssues.isNotEmpty() }

        assertEquals(setOf(ParentModeSetupIssue.PinNotVerified), viewModel.state.value.setupIssues)
        assertNull(store.read())
    }

    @Test
    fun startRequiresVerifiedPinBeforePersistingSession() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(sessionStore = store)
        viewModel.setAllowedApps(setOf("com.video.app"))

        viewModel.startParentMode(pinState = ParentModePinState.NotConfigured)
        awaitUntil { viewModel.state.value.setupIssues.isNotEmpty() }

        assertEquals(setOf(ParentModeSetupIssue.PinNotVerified), viewModel.state.value.setupIssues)
        assertNull(store.read())
    }

    @Test
    fun startWithVerifiedPinPersistsSessionAndEmitsStartedEffect() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { 1_000L },
        )
        viewModel.setDurationMinutes(20)
        viewModel.setAllowedApps(setOf("com.video.app", "com.kids.app"))

        viewModel.startParentMode(pinState = ParentModePinState.Verified)
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        assertEquals(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 1_201_000L,
                durationMinutes = 20,
                allowedApps = setOf("com.video.app", "com.kids.app"),
                state = ParentModeSessionState.Active,
            ),
            store.read(),
        )
        assertEquals(ParentModeSetupSideEffect.Started, viewModel.sideEffect.value)
    }

    /**
     * The duration used to live in three places at once — the header label, the selected chip and a
     * separate number field — so the wheel is now the only place the value is dialled.
     */
    @Test
    fun durationWheelStartsParentModeWithTheHourAndMinuteTheParentDialled() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { 1_000L },
        )
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")

        viewModel.setDurationParts(hours = 0, minutes = 45)

        assertEquals(45, viewModel.state.value.durationMinutes)
        assertEquals(0, viewModel.state.value.durationHoursPart)
        assertEquals(45, viewModel.state.value.durationMinutesPart)
        assertTrue(viewModel.state.value.canAttemptStart)

        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        assertEquals(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 2_701_000L,
                durationMinutes = 45,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
            store.read(),
        )
    }

    @Test
    fun durationWheelCarriesHoursIntoTheStoredSessionMinutes() {
        val viewModel = createViewModel()

        viewModel.setDurationParts(hours = 1, minutes = 15)

        assertEquals(75, viewModel.state.value.durationMinutes)
        assertEquals(1, viewModel.state.value.durationHoursPart)
        assertEquals(15, viewModel.state.value.durationMinutesPart)
    }

    /** Presets are a shortcut into the wheel, not a second copy of the value. */
    @Test
    fun presetDurationReplacesWhateverTheWheelWasShowing() {
        val viewModel = createViewModel()
        viewModel.setDurationParts(hours = 1, minutes = 15)

        viewModel.setDurationMinutes(30)

        assertEquals(30, viewModel.state.value.durationMinutes)
        assertEquals(0, viewModel.state.value.durationHoursPart)
        assertEquals(30, viewModel.state.value.durationMinutesPart)
    }

    /**
     * The PIN is the gate that hands the phone over, not another setting on the form. The form is
     * complete without it, and the CTA opens the gate rather than starting the session.
     */
    @Test
    fun theStartCtaOpensTheGuardianSheetInsteadOfStartingTheSessionOutright() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(sessionStore = store, nowMillis = { 1_000L })
        viewModel.setAllowedApps(setOf("com.video.app"))

        assertTrue(viewModel.state.value.canRequestStart)
        assertNull(viewModel.state.value.pendingGuardianAction)

        viewModel.requestGuardianAction(ParentModeGuardianAction.Start)

        assertEquals(ParentModeGuardianAction.Start, viewModel.state.value.pendingGuardianAction)
        assertNull(store.read())
    }

    @Test
    fun confirmingTheGuardianSheetWithAMatchingPinStartsTheSessionAndClosesTheSheet() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(sessionStore = store, nowMillis = { 1_000L })
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.requestGuardianAction(ParentModeGuardianAction.Start)
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")

        viewModel.confirmPendingGuardianAction()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        assertNull(viewModel.state.value.pendingGuardianAction)
        assertEquals(ParentModeSessionState.Active, store.read()?.state)
    }

    @Test
    fun aMismatchedPinKeepsTheGuardianSheetOpenAndLeavesTheSessionAlone() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(sessionStore = store, nowMillis = { 1_000L })
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.requestGuardianAction(ParentModeGuardianAction.Start)
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("9999")

        viewModel.confirmPendingGuardianAction()
        awaitUntil { ParentModeSetupIssue.PinNotVerified in viewModel.state.value.setupIssues }

        assertEquals(ParentModeGuardianAction.Start, viewModel.state.value.pendingGuardianAction)
        assertNull(store.read())
    }

    /** A PIN typed into one sheet must not still be sitting there when the next sheet opens. */
    @Test
    fun dismissingOrReopeningTheGuardianSheetClearsTheTypedPin() {
        val viewModel = createViewModel()
        viewModel.requestGuardianAction(ParentModeGuardianAction.Start)
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")

        viewModel.dismissGuardianAction()

        assertNull(viewModel.state.value.pendingGuardianAction)
        assertEquals("", viewModel.state.value.guardianPin)
        assertEquals("", viewModel.state.value.guardianPinConfirmation)

        viewModel.updateGuardianPin("5678")
        viewModel.requestGuardianAction(ParentModeGuardianAction.End)

        assertEquals("", viewModel.state.value.guardianPin)
    }

    @Test
    fun theGuardianSheetRoutesExtendAndEndThroughTheSamePinGate() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        var now = 1_000L
        val viewModel = createViewModel(sessionStore = store, nowMillis = { now })
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.setDurationMinutes(10)
        viewModel.requestGuardianAction(ParentModeGuardianAction.Start)
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.confirmPendingGuardianAction()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        now = 60_000L
        viewModel.requestGuardianAction(ParentModeGuardianAction.Extend)
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.confirmPendingGuardianAction()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Extended }

        assertNull(viewModel.state.value.pendingGuardianAction)
        assertEquals(20, store.read()?.durationMinutes)

        viewModel.requestGuardianAction(ParentModeGuardianAction.End)
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.confirmPendingGuardianAction()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Ended }

        assertNull(viewModel.state.value.pendingGuardianAction)
        assertEquals(ParentModeSessionState.UnlockedByPin, store.read()?.state)
    }

    @Test
    fun openingParentModeSetupRestoresThePersistedActiveSession() = runBlocking {
        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 601_000L,
            durationMinutes = 10,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )
        val store = ParentModeSessionStore(FakeDataStore())
        store.save(expectedSession)
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { 120_000L },
        )

        viewModel.refreshActiveSessionStatus()
        awaitUntil { viewModel.state.value.activeSession != null }

        assertEquals(expectedSession, viewModel.state.value.activeSession)
    }

    @Test
    fun activeSessionControlsRequireFreshVerifiedPinBeforeExtending() = runBlocking {
        var now = 1_000L
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { now },
        )
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        assertEquals("", viewModel.state.value.guardianPin)
        assertEquals("", viewModel.state.value.guardianPinConfirmation)

        now = 120_000L
        viewModel.extendActiveSessionByTenMinutes()
        awaitUntil { viewModel.state.value.setupIssues == setOf(ParentModeSetupIssue.PinNotVerified) }

        assertEquals(ParentModeSetupSideEffect.Started, viewModel.sideEffect.value)
        assertEquals(601_000L, store.read()?.expiresAtMillis)

        viewModel.updateGuardianPin("4321")
        viewModel.updateGuardianPinConfirmation("4321")
        viewModel.extendActiveSessionByTenMinutes()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Extended }

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 1_201_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )
        assertEquals(expectedSession, viewModel.state.value.activeSession)
        assertEquals(expectedSession, store.read())
    }

    @Test
    fun activeSessionCanBeEndedWithVerifiedPinFromSetupScreen() = runBlocking {
        var now = 1_000L
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { now },
        )
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        now = 300_000L
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.endActiveSessionFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Ended }

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 300_000L,
            durationMinutes = 10,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.UnlockedByPin,
        )
        assertEquals(expectedSession, viewModel.state.value.activeSession)
        assertEquals(expectedSession, store.read())
    }

    @Test
    fun expiredActiveSessionIsMarkedBeforeRenderingControls() = runBlocking {
        var now = 1_000L
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { now },
        )
        viewModel.setDurationMinutes(1)
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        now = 61_000L
        viewModel.refreshActiveSessionStatus()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Expired }

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Expired,
        )
        assertEquals(expectedSession, viewModel.state.value.activeSession)
        assertEquals(expectedSession, store.read())
    }

    @Test
    fun extendingExpiredSessionFromControlsShowsExpiredStateInsteadOfLeavingStaleActiveButtons() = runBlocking {
        var now = 1_000L
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { now },
        )
        viewModel.setDurationMinutes(1)
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        now = 61_000L
        viewModel.extendActiveSessionByTenMinutes()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Expired }

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Expired,
        )
        assertEquals(expectedSession, viewModel.state.value.activeSession)
        assertEquals(expectedSession, store.read())
    }

    @Test
    fun endingExpiredSessionFromControlsShowsExpiredStateInsteadOfPinEndedState() = runBlocking {
        var now = 1_000L
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { now },
        )
        viewModel.setDurationMinutes(1)
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        now = 61_000L
        viewModel.endActiveSessionFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Expired }

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Expired,
        )
        assertEquals(expectedSession, viewModel.state.value.activeSession)
        assertEquals(expectedSession, store.read())
    }

    @Test
    fun inactiveSessionCanBeClearedForAnotherParentModeSetup() = runBlocking {
        var now = 1_000L
        val store = ParentModeSessionStore(FakeDataStore())
        val viewModel = createViewModel(
            sessionStore = store,
            nowMillis = { now },
        )
        viewModel.setDurationMinutes(1)
        viewModel.setAllowedApps(setOf("com.video.app"))
        viewModel.updateGuardianPin("1234")
        viewModel.updateGuardianPinConfirmation("1234")
        viewModel.startParentModeFromSetupInput()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Started }

        now = 61_000L
        viewModel.refreshActiveSessionStatus()
        awaitUntil { viewModel.sideEffect.value == ParentModeSetupSideEffect.Expired }

        viewModel.prepareAnotherParentModeSession()
        awaitUntil { viewModel.state.value.activeSession == null }

        assertNull(store.read())
        assertEquals(setOf("com.video.app"), viewModel.state.value.allowedApps)
        assertEquals("", viewModel.state.value.guardianPin)
        assertEquals("", viewModel.state.value.guardianPinConfirmation)
        assertTrue(viewModel.state.value.canAttemptStart.not())
    }

    private fun createViewModel(
        sessionStore: ParentModeSessionStore = ParentModeSessionStore(FakeDataStore()),
        nowMillis: () -> Long = { 10_000L },
        analytics: ParentModeSetupRecordingAnalytics = ParentModeSetupRecordingAnalytics(),
    ): ParentModeSetupViewModel = ParentModeSetupViewModel(
        sessionController = ParentModeSessionController(sessionStore, analytics),
        clock = object : ParentModeClock() {
            override fun nowMillis(): Long = nowMillis()
        },
        analytics = analytics,
    )

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(40) {
            if (predicate()) return
            delay(25)
        }
        check(predicate()) { "condition was not met" }
    }
}

private class ParentModeSetupRecordingAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) {
        screenViews += screenName
    }
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}
