package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
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
    fun loadAllowedAppsFromCurrentBlockingSelectionSeedsParentModeSetup() = runBlocking {
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.SELECTED_APP_PACKAGES] = setOf("com.video.app", "com.kids.app")
        }
        val viewModel = createViewModel(blockingStateStore = BlockingStateStore(dataStore))

        viewModel.loadAllowedAppsFromCurrentSelection()
        awaitUntil { viewModel.state.value.allowedApps.isNotEmpty() }

        assertEquals(setOf("com.video.app", "com.kids.app"), viewModel.state.value.allowedApps)
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

    private fun createViewModel(
        blockingStateStore: BlockingStateStore = BlockingStateStore(FakeDataStore()),
        sessionStore: ParentModeSessionStore = ParentModeSessionStore(FakeDataStore()),
        nowMillis: () -> Long = { 10_000L },
    ): ParentModeSetupViewModel = ParentModeSetupViewModel(
        blockingStateStore = blockingStateStore,
        sessionController = ParentModeSessionController(sessionStore, NoOpParentModeAnalytics()),
        clock = object : ParentModeClock() {
            override fun nowMillis(): Long = nowMillis()
        },
    )

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(40) {
            if (predicate()) return
            delay(25)
        }
        check(predicate()) { "condition was not met" }
    }
}

private class NoOpParentModeAnalytics : KeepAnalytics {
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
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
