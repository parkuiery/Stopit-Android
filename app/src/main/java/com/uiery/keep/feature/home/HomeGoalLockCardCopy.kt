package com.uiery.keep.feature.home

import androidx.annotation.StringRes
import com.uiery.keep.R

data class HomeGoalLockCardDisplayCopy(
    @StringRes val titleResId: Int,
    @StringRes val summaryResId: Int,
    @StringRes val lockModeResId: Int,
)

fun HomeGoalLockCardState.displayCopy(): HomeGoalLockCardDisplayCopy = HomeGoalLockCardDisplayCopy(
    titleResId = when (status) {
        HomeGoalLockStatus.Pending -> R.string.home_goal_lock_card_title_pending
        HomeGoalLockStatus.Active -> R.string.home_goal_lock_card_title_active
        HomeGoalLockStatus.Completed -> R.string.home_goal_lock_card_title_completed
        HomeGoalLockStatus.EndedEarly -> R.string.home_goal_lock_card_title_ended_early
    },
    summaryResId = when {
        status == HomeGoalLockStatus.Active &&
            lockMode == HomeGoalLockCardLockMode.Scheduled &&
            !isCurrentlyProtecting -> R.string.home_goal_lock_card_summary_active_waiting_window
        status == HomeGoalLockStatus.Pending -> R.string.home_goal_lock_card_summary_pending
        status == HomeGoalLockStatus.Active -> R.string.home_goal_lock_card_summary_active
        status == HomeGoalLockStatus.Completed -> R.string.home_goal_lock_card_summary_completed
        status == HomeGoalLockStatus.EndedEarly -> R.string.home_goal_lock_card_summary_ended_early
        else -> R.string.home_goal_lock_card_summary_active
    },
    lockModeResId = when (lockMode) {
        HomeGoalLockCardLockMode.AllDay -> R.string.home_goal_lock_card_lock_mode_all_day
        HomeGoalLockCardLockMode.Scheduled -> R.string.home_goal_lock_card_lock_mode_scheduled
    },
)
