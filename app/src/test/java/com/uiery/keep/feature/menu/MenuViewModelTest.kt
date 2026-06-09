package com.uiery.keep.feature.menu

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.AccessibilityBlockingSnapshot
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuViewModelTest {
    @Test
    fun initLogsMenuScreenView() {
        val analytics = MenuRecordingKeepAnalytics()

        MenuViewModel(
            blockingStateStore = BlockingStateStore(FakeDataStore()),
            routineRepository = FakeMenuRoutineRepository(),
            analytics = analytics,
        )

        assertEquals(listOf(KeepAnalyticsScreen.MENU), analytics.screenViews)
    }

    @Test
    fun preventUninstallDefaultsToEnabled() {
        val viewModel = MenuViewModel(
            blockingStateStore = BlockingStateStore(FakeDataStore()),
            routineRepository = FakeMenuRoutineRepository(),
            analytics = MenuRecordingKeepAnalytics(),
        )

        assertTrue(viewModel.preventUninstall.value)
    }

    @Test
    fun setPreventUninstallPersistsDisabledValue() = runBlocking {
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.PREVENT_UNINSTALL] = true
        }
        val viewModel = MenuViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            routineRepository = FakeMenuRoutineRepository(),
            analytics = MenuRecordingKeepAnalytics(),
        )

        viewModel.setPreventUninstall(false)

        repeat(20) {
            if ((dataStore.snapshot()[PreferencesKey.PREVENT_UNINSTALL] ?: true).not()) {
                return@runBlocking
            }
            delay(25)
        }

        assertFalse(
            dataStore.snapshot()[PreferencesKey.PREVENT_UNINSTALL] ?: true,
        )
    }

    @Test
    fun isBlockingIncludesActiveAllDayGoalLock() = runBlocking {
        val isBlocking = isBlockingActive(
            snapshot = AccessibilityBlockingSnapshot(),
            routines = emptyList(),
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.now().minusDays(1),
                    endDate = LocalDate.now().plusDays(1),
                    lockMode = GoalLockMode.AllDay,
                ),
            ),
        )

        assertTrue(isBlocking)
    }

    @Test
    fun isBlockingIgnoresGoalLocksThatAreNotCurrentlyBlocking() = runBlocking {
        val isBlocking = isBlockingActive(
            snapshot = AccessibilityBlockingSnapshot(),
            routines = emptyList(),
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.now().plusDays(1),
                    endDate = LocalDate.now().plusDays(2),
                    lockMode = GoalLockMode.AllDay,
                ),
                goalLock(
                    id = 2L,
                    startDate = LocalDate.now().minusDays(1),
                    endDate = LocalDate.now().plusDays(1),
                    selectedPackages = emptySet(),
                ),
                goalLock(
                    id = 3L,
                    startDate = LocalDate.now().minusDays(1),
                    endDate = LocalDate.now().plusDays(1),
                    status = GoalLockStoredStatus.Completed,
                ),
            ),
        )

        assertFalse(isBlocking)
    }

    @Test
    fun monetizationInterestCardShownUsesMenuSettingsContext() {
        val analytics = MenuRecordingKeepAnalytics()
        val viewModel = MenuViewModel(
            blockingStateStore = BlockingStateStore(FakeDataStore()),
            routineRepository = FakeMenuRoutineRepository(),
            analytics = analytics,
        )

        viewModel.onMonetizationInterestCardShown()

        assertEquals(
            listOf(
                MonetizationInterestEvent(
                    type = "shown",
                    surface = "menu",
                    context = "menu_settings",
                    variant = "default",
                    purchaseAvailable = false,
                ),
            ),
            analytics.monetizationInterestEvents,
        )
    }

    @Test
    fun monetizationInterestCardClickedUsesMenuSettingsContext() {
        val analytics = MenuRecordingKeepAnalytics()
        val viewModel = MenuViewModel(
            blockingStateStore = BlockingStateStore(FakeDataStore()),
            routineRepository = FakeMenuRoutineRepository(),
            analytics = analytics,
        )

        viewModel.onMonetizationInterestCardClicked()

        assertEquals(
            listOf(
                MonetizationInterestEvent(
                    type = "clicked",
                    surface = "menu",
                    context = "menu_settings",
                    variant = "default",
                    purchaseAvailable = false,
                ),
            ),
            analytics.monetizationInterestEvents,
        )
    }
}

private class FakeMenuRoutineRepository(
    routines: List<RoutineModel> = emptyList(),
) : RoutineRepository {
    private val state = MutableStateFlow(routines)

    override fun fetchAll(): Flow<List<RoutineModel>> = state
}

private fun goalLock(
    id: Long = 1L,
    startDate: LocalDate,
    endDate: LocalDate,
    lockMode: GoalLockMode = GoalLockMode.AllDay,
    selectedPackages: Set<String> = setOf("com.video.app"),
    status: GoalLockStoredStatus = GoalLockStoredStatus.Active,
) = GoalLock(
    id = id,
    goalName = "Goal $id",
    startDate = startDate,
    endDate = endDate,
    lockMode = lockMode,
    selectedPackages = selectedPackages,
    status = status,
)

private data class MonetizationInterestEvent(
    val type: String,
    val surface: String,
    val context: String,
    val variant: String?,
    val purchaseAvailable: Boolean?,
)

private class MenuRecordingKeepAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    val monetizationInterestEvents = mutableListOf<MonetizationInterestEvent>()

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

    override fun trackMonetizationInterestShown(
        interestSurface: String,
        interestContext: String,
        interestVariant: String?,
        purchaseAvailable: Boolean?,
    ) {
        monetizationInterestEvents += MonetizationInterestEvent(
            type = "shown",
            surface = interestSurface,
            context = interestContext,
            variant = interestVariant,
            purchaseAvailable = purchaseAvailable,
        )
    }

    override fun trackMonetizationInterestClicked(
        interestSurface: String,
        interestContext: String,
        interestVariant: String?,
        purchaseAvailable: Boolean?,
    ) {
        monetizationInterestEvents += MonetizationInterestEvent(
            type = "clicked",
            surface = interestSurface,
            context = interestContext,
            variant = interestVariant,
            purchaseAvailable = purchaseAvailable,
        )
    }
}
