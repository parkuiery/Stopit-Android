package com.uiery.keep.feature.menu

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import com.uiery.keep.feature.review.FakeDataStore
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
            routineDao = FakeMenuRoutineDao(),
            analytics = analytics,
        )

        assertEquals(listOf(KeepAnalyticsScreen.MENU), analytics.screenViews)
    }

    @Test
    fun preventUninstallDefaultsToEnabled() {
        val viewModel = MenuViewModel(
            blockingStateStore = BlockingStateStore(FakeDataStore()),
            routineDao = FakeMenuRoutineDao(),
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
            routineDao = FakeMenuRoutineDao(),
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
}

private class FakeMenuRoutineDao(
    routines: List<RoutineEntity> = emptyList(),
) : RoutineDao {
    private val state = MutableStateFlow(routines)

    override fun fetchAll(): Flow<List<RoutineEntity>> = state
    override fun fetchAllOnce(): List<RoutineEntity> = state.value
    override fun fetch(id: Long): RoutineEntity = state.value.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private class MenuRecordingKeepAnalytics : KeepAnalytics {
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
