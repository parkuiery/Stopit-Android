package com.uiery.keep.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsMonetizationInterestContext
import com.uiery.keep.analytics.AnalyticsMonetizationInterestSurface
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.AccessibilityBlockingSnapshot
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.feature.goallock.GoalLockRepository
import com.uiery.keep.feature.routine.RoutineRepository
import com.uiery.keep.util.RoutineRuntimePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class MenuViewModel private constructor(
    private val blockingStateStore: BlockingStateStore,
    private val routineRepository: RoutineRepository,
    goalLocks: Flow<List<GoalLock>>,
    private val analytics: KeepAnalytics,
    @Suppress("UNUSED_PARAMETER") private val constructorMarker: Unit,
) : ViewModel() {
    @Inject
    constructor(
        blockingStateStore: BlockingStateStore,
        routineRepository: RoutineRepository,
        goalLockRepository: GoalLockRepository,
        analytics: KeepAnalytics,
    ) : this(
        blockingStateStore = blockingStateStore,
        routineRepository = routineRepository,
        goalLocks = goalLockRepository.fetchAll(),
        analytics = analytics,
        constructorMarker = Unit,
    )

    internal constructor(
        blockingStateStore: BlockingStateStore,
        routineRepository: RoutineRepository,
        goalLocks: Flow<List<GoalLock>> = kotlinx.coroutines.flow.flowOf(emptyList()),
        analytics: KeepAnalytics,
    ) : this(
        blockingStateStore = blockingStateStore,
        routineRepository = routineRepository,
        goalLocks = goalLocks,
        analytics = analytics,
        constructorMarker = Unit,
    )

    init {
        analytics.logScreenView(KeepAnalyticsScreen.MENU)
    }

    val preventUninstall: StateFlow<Boolean> = blockingStateStore.accessibilitySnapshot
        .map { it.preventUninstall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBlocking: StateFlow<Boolean> = combine(
        blockingStateStore.accessibilitySnapshot,
        routineRepository.fetchAll(),
        goalLocks,
    ) { snapshot, routines, goalLocks ->
            isBlockingActive(snapshot = snapshot, routines = routines, goalLocks = goalLocks)
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

    fun onSupportContactStarted() {
        analytics.trackSupportContactStarted(surface = SupportContactSurface.MENU)
    }

    fun onSupportContactClipboardFallbackUsed() {
        analytics.trackSupportContactFallbackUsed(
            surface = SupportContactSurface.MENU,
            fallbackType = SupportContactFallbackType.CLIPBOARD,
        )
    }

    private companion object {
        const val MONETIZATION_INTEREST_VARIANT = "default"
    }
}

internal fun isBlockingActive(
    snapshot: AccessibilityBlockingSnapshot,
    routines: List<com.uiery.keep.model.RoutineModel>,
    goalLocks: List<GoalLock>,
): Boolean {
    val isLockTime = ManualLockTimePolicy.isActiveAt(snapshot.lockTime)
    val isRoutineActive = RoutineRuntimePolicy.isAnyRoutineActive(routines)
    val isGoalLockActive = goalLocks.any { goalLock ->
        goalLock.selectedPackages.any { packageName ->
            GoalLockPolicy.isBlocking(goalLock = goalLock, packageName = packageName)
        }
    }
    return snapshot.isKeep || isLockTime || isRoutineActive || isGoalLockActive
}
