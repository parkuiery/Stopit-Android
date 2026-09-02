package com.uiery.keep.feature.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.usageinsight.UsageInsightRoutinePrefill
import kotlinx.serialization.Serializable

/**
 * [openAppSelection] 은 다른 화면에서 "막을 앱이 없다"는 막다른 길을 만났을 때, 홈으로 돌려보내는
 * 데서 그치지 않고 선택 시트까지 열어 주기 위한 것이다. 앱 선택은 홈의 바텀시트가 소유하고 있어
 * 다른 화면에서 직접 띄울 수 없다.
 */
@Serializable
data class HomeRoute(val openAppSelection: Boolean = false)

fun NavController.navigateToHome(
    openAppSelection: Boolean = false,
    navOptions: NavOptions = navOptions {
        popUpTo(graph.id) {
            inclusive = true
        }
    }
) = navigate(route = HomeRoute(openAppSelection = openAppSelection), navOptions = navOptions)

fun NavGraphBuilder.homeScreen(
    onNavigateMenu: () -> Unit,
    onNavigatePomodoro: (autoStart: Boolean) -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
    onNavigateLockHistory: () -> Unit,
    onNavigateRoutine: (routineSavedEntrySurface: String?, routineSavedCreationSource: String?) -> Unit,
    onNavigateGoalLockDetail: (goalLockId: Long) -> Unit,
    onNavigateRoutineWithRepeatBlockPrefill: (RepeatBlockRoutineSuggestion) -> Unit,
    onNavigateRoutineWithUsageInsightPrefill: (UsageInsightRoutinePrefill) -> Unit,
) {
    composable<HomeRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<HomeRoute>()
        HomeScreen(
            openAppSelectionOnEntry = route.openAppSelection,
            onNavigateMenu = onNavigateMenu,
            onNavigatePomodoro = onNavigatePomodoro,
            onNavigateLock = onNavigateLock,
            onNavigateLockHistory = onNavigateLockHistory,
            onNavigateRoutine = onNavigateRoutine,
            onNavigateGoalLockDetail = onNavigateGoalLockDetail,
            onNavigateRoutineWithRepeatBlockPrefill = onNavigateRoutineWithRepeatBlockPrefill,
            onNavigateRoutineWithUsageInsightPrefill = onNavigateRoutineWithUsageInsightPrefill,
        )
    }
}
