package com.uiery.keep.feature.review

enum class SkipReason {
    KillSwitch,
    Debug,
    DevFlavor,
    NoGooglePlay,
    BelowSessionThreshold,
    RecentEmergencyUnlock,
    WithinCooldown,
    AlreadyToday,
    WithinSameSession,
    AccessibilityOff,
    NotificationOff,
    NoRecentSuccess,
    NotHomeRoot,
    QuietHours,
    NoBackgroundingObserved,
}

sealed interface ReviewEligibilityDecision {
    data object Eligible : ReviewEligibilityDecision
    data class Ineligible(val reason: SkipReason) : ReviewEligibilityDecision
}
