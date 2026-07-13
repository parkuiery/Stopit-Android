package com.uiery.keep.feature.goallock

internal enum class GoalLockEditBackSource {
    System,
    TopBar,
}

internal enum class GoalLockEditBackAction {
    RequestBack,
}

internal fun goalLockEditBackAction(source: GoalLockEditBackSource): GoalLockEditBackAction =
    when (source) {
        GoalLockEditBackSource.System,
        GoalLockEditBackSource.TopBar,
        -> GoalLockEditBackAction.RequestBack
    }

internal fun buildGoalLockEditAccessibilityDescription(
    goalName: String,
    durationRangeText: String,
    lockModeText: String,
    selectedAppsText: String,
): String = listOf(
    goalName.trim(),
    durationRangeText.trim(),
    lockModeText.trim(),
    selectedAppsText.trim(),
)
    .filter(String::isNotBlank)
    .joinToString(", ")
