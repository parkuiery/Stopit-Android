package com.uiery.keep.feature.pomodoro

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uiery.keep.service.PomodoroSessionService
import kotlinx.serialization.Serializable

/**
 * [autoStart] 는 시트에서 이미 "집중 시작"을 누르고 들어온 경우다. 설정을 한 번 더 확인시키면
 * 같은 결정을 두 번 누르게 된다.
 */
@Serializable
data class PomodoroRoute(val autoStart: Boolean = false)

fun NavController.navigateToPomodoro(autoStart: Boolean = false) =
    navigate(route = PomodoroRoute(autoStart = autoStart))

fun NavGraphBuilder.pomodoroScreen(
    onNavigateHome: () -> Unit,
    onPickApps: () -> Unit,
) {
    composable<PomodoroRoute> { backStackEntry ->
        val context = LocalContext.current
        PomodoroScreen(
            autoStart = backStackEntry.toRoute<PomodoroRoute>().autoStart,
            onNavigateHome = onNavigateHome,
            onPickApps = onPickApps,
            onStartSessionService = { PomodoroSessionService.start(context) },
            onStopSessionService = { PomodoroSessionService.stop(context) },
        )
    }
}
