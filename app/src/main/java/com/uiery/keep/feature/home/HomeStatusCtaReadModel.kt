package com.uiery.keep.feature.home

import androidx.annotation.StringRes
import com.uiery.keep.R

data class HomeStatusCtaModel(
    val statusKind: HomeStatusKind,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    @StringRes val primaryCtaResId: Int,
    val selectedAppCount: Int,
    val shouldOpenAppSelection: Boolean,
    val shouldToggleKeep: Boolean,
    val timerEnabled: Boolean,
    val showChangeAppsSecondary: Boolean,
    val showLockHistorySecondary: Boolean,
    val showRoutineCreationSecondary: Boolean,
    val showGoalLockStatus: Boolean,
)

enum class HomeStatusKind {
    NO_SELECTED_APPS,
    FIRST_LOCK_READY,
    READY,
    KEEP_ACTIVE,
}

fun buildHomeStatusCtaModel(
    isKeep: Boolean,
    selectedAppCount: Int,
    showFirstLockActivationCta: Boolean,
    showRoutineCreationCta: Boolean,
    hasGoalLockCard: Boolean,
): HomeStatusCtaModel {
    val hasSelectedApps = selectedAppCount > 0
    val statusKind = when {
        isKeep -> HomeStatusKind.KEEP_ACTIVE
        !hasSelectedApps -> HomeStatusKind.NO_SELECTED_APPS
        showFirstLockActivationCta -> HomeStatusKind.FIRST_LOCK_READY
        else -> HomeStatusKind.READY
    }

    return when (statusKind) {
        HomeStatusKind.NO_SELECTED_APPS -> HomeStatusCtaModel(
            statusKind = statusKind,
            titleResId = R.string.home_status_no_selected_apps_title,
            descriptionResId = R.string.home_status_no_selected_apps_description,
            primaryCtaResId = R.string.home_primary_cta_select_apps,
            selectedAppCount = selectedAppCount,
            shouldOpenAppSelection = true,
            shouldToggleKeep = false,
            timerEnabled = false,
            showChangeAppsSecondary = false,
            showLockHistorySecondary = false,
            showRoutineCreationSecondary = false,
            showGoalLockStatus = hasGoalLockCard,
        )
        HomeStatusKind.FIRST_LOCK_READY -> HomeStatusCtaModel(
            statusKind = statusKind,
            titleResId = R.string.home_status_first_lock_ready_title,
            descriptionResId = R.string.home_status_first_lock_ready_description,
            primaryCtaResId = R.string.home_primary_cta_start_now,
            selectedAppCount = selectedAppCount,
            shouldOpenAppSelection = false,
            shouldToggleKeep = true,
            timerEnabled = true,
            showChangeAppsSecondary = true,
            showLockHistorySecondary = false,
            showRoutineCreationSecondary = false,
            showGoalLockStatus = hasGoalLockCard,
        )
        HomeStatusKind.READY -> HomeStatusCtaModel(
            statusKind = statusKind,
            titleResId = R.string.home_status_ready_title,
            descriptionResId = R.string.home_status_ready_description,
            primaryCtaResId = R.string.home_primary_cta_start_now,
            selectedAppCount = selectedAppCount,
            shouldOpenAppSelection = false,
            shouldToggleKeep = true,
            timerEnabled = true,
            showChangeAppsSecondary = true,
            showLockHistorySecondary = true,
            showRoutineCreationSecondary = showRoutineCreationCta,
            showGoalLockStatus = hasGoalLockCard,
        )
        HomeStatusKind.KEEP_ACTIVE -> HomeStatusCtaModel(
            statusKind = statusKind,
            titleResId = R.string.home_status_keep_active_title,
            descriptionResId = R.string.home_status_keep_active_description,
            primaryCtaResId = R.string.home_primary_status_keep_active,
            selectedAppCount = selectedAppCount,
            shouldOpenAppSelection = false,
            shouldToggleKeep = false,
            timerEnabled = false,
            showChangeAppsSecondary = true,
            showLockHistorySecondary = true,
            showRoutineCreationSecondary = false,
            showGoalLockStatus = hasGoalLockCard,
        )
    }
}
