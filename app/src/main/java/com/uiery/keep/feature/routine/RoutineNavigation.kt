package com.uiery.keep.feature.routine

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class RoutineRoute(
    val repeatBlockSurface: String? = null,
    val repeatBlockReason: String? = null,
    val repeatBlockTimeBucket: String? = null,
    val repeatBlockDayType: String? = null,
    val repeatBlockCategoryBucket: String? = null,
    val repeatBlockCountBucket: String? = null,
    val repeatBlockCoverageState: String? = null,
    val prefillPackages: List<String> = emptyList(),
    val prefillStartHour: Int? = null,
    val prefillStartMinute: Int? = null,
    val prefillEndHour: Int? = null,
    val prefillEndMinute: Int? = null,
)

fun NavController.navigateToRoutine(
    navOptions: NavOptions? = null,
) = navigate(
    route = RoutineRoute(),
    navOptions = navOptions,
)

fun NavController.navigateToRoutineWithRepeatBlockPrefill(
    surface: String,
    suggestion: RepeatBlockRoutineSuggestion,
    navOptions: NavOptions? = null,
) = navigate(
    route = RoutineRoute(
        repeatBlockSurface = surface,
        repeatBlockReason = suggestion.reason.analyticsValue,
        repeatBlockTimeBucket = suggestion.timeBucket.analyticsValue,
        repeatBlockDayType = suggestion.dayType.analyticsValue,
        repeatBlockCategoryBucket = suggestion.categoryBucket.analyticsValue,
        repeatBlockCountBucket = suggestion.repeatCountBucket.analyticsValue,
        repeatBlockCoverageState = suggestion.routineCoverageState.analyticsValue,
        prefillPackages = suggestion.prefillPackages,
        prefillStartHour = suggestion.prefillStartTime.hour,
        prefillStartMinute = suggestion.prefillStartTime.minute,
        prefillEndHour = suggestion.prefillEndTime.hour,
        prefillEndMinute = suggestion.prefillEndTime.minute,
    ),
    navOptions = navOptions,
)

fun NavGraphBuilder.routineScreen(
    onNavigateBack: () -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
) {
    composable<RoutineRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<RoutineRoute>()
        RoutineScreen(
            repeatBlockSuggestionSurface = route.repeatBlockSurface,
            repeatBlockSuggestion = route.toRepeatBlockRoutineSuggestionOrNull(),
            onNavigateBack = onNavigateBack,
            onNavigateLock = onNavigateLock,
        )
    }
}

internal fun RoutineRoute.toRepeatBlockRoutineSuggestionOrNull(): RepeatBlockRoutineSuggestion? {
    val reason = enumByAnalyticsValue<RepeatBlockSuggestionReason>(repeatBlockReason) ?: return null
    val timeBucket = enumByAnalyticsValue<RepeatBlockTimeBucket>(repeatBlockTimeBucket) ?: return null
    val dayType = enumByAnalyticsValue<RepeatBlockDayType>(repeatBlockDayType) ?: return null
    val categoryBucket = enumByAnalyticsValue<RepeatBlockCategoryBucket>(repeatBlockCategoryBucket) ?: return null
    val countBucket = enumByAnalyticsValue<RepeatBlockCountBucket>(repeatBlockCountBucket) ?: return null
    val coverageState = enumByAnalyticsValue<RoutineCoverageState>(repeatBlockCoverageState) ?: return null
    val startHour = prefillStartHour ?: return null
    val startMinute = prefillStartMinute ?: return null
    val endHour = prefillEndHour ?: return null
    val endMinute = prefillEndMinute ?: return null
    if (prefillPackages.isEmpty()) return null

    return RepeatBlockRoutineSuggestion(
        reason = reason,
        timeBucket = timeBucket,
        dayType = dayType,
        categoryBucket = categoryBucket,
        repeatCountBucket = countBucket,
        routineCoverageState = coverageState,
        prefillPackages = prefillPackages,
        prefillStartTime = LocalTime(startHour, startMinute),
        prefillEndTime = LocalTime(endHour, endMinute),
    )
}

private inline fun <reified T : Enum<T>> enumByAnalyticsValue(value: String?): T? =
    enumValues<T>().firstOrNull { enumValue ->
        when (enumValue) {
            is RepeatBlockSuggestionReason -> enumValue.analyticsValue == value
            is RepeatBlockTimeBucket -> enumValue.analyticsValue == value
            is RepeatBlockDayType -> enumValue.analyticsValue == value
            is RepeatBlockCategoryBucket -> enumValue.analyticsValue == value
            is RepeatBlockCountBucket -> enumValue.analyticsValue == value
            is RoutineCoverageState -> enumValue.analyticsValue == value
            else -> false
        }
    }
