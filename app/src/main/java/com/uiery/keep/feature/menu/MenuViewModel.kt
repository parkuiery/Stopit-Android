package com.uiery.keep.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsMonetizationInterestContext
import com.uiery.keep.analytics.AnalyticsMonetizationInterestSurface
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.feature.routine.RoutineRepository
import com.uiery.keep.util.RoutineRuntimePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val routineRepository: RoutineRepository,
    private val analytics: KeepAnalytics,
) : ViewModel() {

    init {
        analytics.logScreenView(KeepAnalyticsScreen.MENU)
    }

    val preventUninstall: StateFlow<Boolean> = blockingStateStore.accessibilitySnapshot
        .map { it.preventUninstall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBlocking: StateFlow<Boolean> = combine(blockingStateStore.accessibilitySnapshot, routineRepository.fetchAll()) { snapshot, routines ->
            val isLockTime = ManualLockTimePolicy.isActiveAt(snapshot.lockTime)
            val isRoutineActive = RoutineRuntimePolicy.isAnyRoutineActive(routines)
            snapshot.isKeep || isLockTime || isRoutineActive
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPreventUninstall(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            blockingStateStore.setPreventUninstall(enabled)
        }
    }

    fun onMonetizationInterestCardShown() {
        analytics.trackMonetizationInterestShown(
            interestSurface = AnalyticsMonetizationInterestSurface.MENU,
            interestContext = AnalyticsMonetizationInterestContext.MENU_SETTINGS,
            interestVariant = MONETIZATION_INTEREST_VARIANT,
            purchaseAvailable = false,
        )
    }

    fun onMonetizationInterestCardClicked() {
        analytics.trackMonetizationInterestClicked(
            interestSurface = AnalyticsMonetizationInterestSurface.MENU,
            interestContext = AnalyticsMonetizationInterestContext.MENU_SETTINGS,
            interestVariant = MONETIZATION_INTEREST_VARIANT,
            purchaseAvailable = false,
        )
    }

    private companion object {
        const val MONETIZATION_INTEREST_VARIANT = "default"
    }
}
