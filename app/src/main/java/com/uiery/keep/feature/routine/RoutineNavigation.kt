package com.uiery.keep.feature.routine

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uiery.keep.domain.repeatblock.RepeatBlockCategoryBucket
import com.uiery.keep.domain.repeatblock.RepeatBlockCountBucket
import com.uiery.keep.domain.repeatblock.RepeatBlockDayType
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.repeatblock.RepeatBlockSuggestionReason
import com.uiery.keep.analytics.routine.RoutineSavedCreationSource
import com.uiery.keep.domain.repeatblock.RepeatBlockTimeBucket
import com.uiery.keep.domain.repeatblock.RoutineCoverageState
import com.uiery.keep.domain.usageinsight.UsageInsightRoutinePrefill
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class RoutineRoute(
    val routineSavedEntrySurface: String? = null,
    val routineSavedCreationSource: String? = null,
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
    routineSavedEntrySurface: String? = null,
    routineSavedCreationSource: String? = null,
    navOptions: NavOptions? = null,
) = navigate(
    route = RoutineRoute(
        routineSavedEntrySurface = routineSavedEntrySurface,
        routineSavedCreationSource = routineSavedCreationSource,
    ),
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

fun NavController.navigateToRoutineWithUsageInsightPrefill(
    prefill: UsageInsightRoutinePrefill,
    navOptions: NavOptions? = null,
) = navigate(
    route = RoutineRoute(
        routineSavedCreationSource = RoutineSavedCreationSource.USAGE_INSIGHT_PREFILL,
        prefillPackages = prefill.packages,
        prefillStartHour = prefill.startTime.hour,
        prefillStartMinute = prefill.startTime.minute,
        prefillEndHour = prefill.endTime.hour,
        prefillEndMinute = prefill.endTime.minute,
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
            routineSavedEntrySurface = route.routineSavedEntrySurface,
            routineSavedCreationSource = route.routineSavedCreationSource,
            repeatBlockSuggestionSurface = route.repeatBlockSurface,
            repeatBlockSuggestion = route.toRepeatBlockRoutineSuggestionOrNull(),
            usageInsightRoutinePrefill = route.toUsageInsightRoutinePrefillOrNull(),
            onNavigateBack = onNavigateBack,
            onNavigateLock = onNavigateLock,
        )
    }
}

/**
 * usage-insight CTA로 진입한 경우의 prefill 복원. repeatBlock 제안 경로(버킷 존재)와 상호배타적이며,
 * 버킷 없이 prefill 패키지/시간만 담긴 경우에만 값을 돌려준다.
 */
internal fun RoutineRoute.toUsageInsightRoutinePrefillOrNull(): UsageInsightRoutinePrefill? {
    if (routineSavedCreationSource != RoutineSavedCreationSource.USAGE_INSIGHT_PREFILL) return null
    val startHour = prefillStartHour ?: return null
    val startMinute = prefillStartMinute ?: return null
    val endHour = prefillEndHour ?: return null
    val endMinute = prefillEndMinute ?: return null
    if (prefillPackages.isEmpty()) return null

    return UsageInsightRoutinePrefill(
        packages = prefillPackages,
        startTime = LocalTime(startHour, startMinute),
        endTime = LocalTime(endHour, endMinute),
    )
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
