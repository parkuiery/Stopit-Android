package com.uiery.keep.feature.review

enum class SkipReason {
    KillSwitch,
    Debug,
    DevFlavor,
    NoGooglePlay,
    BelowSessionThreshold,

    /**
     * 수동 종료(`user_toggle_off`) 세션이 성공 세션으로 인정되기에 너무 짧았다.
     * 켜자마자 끄는 오조작을 리뷰 후보에서 제외한다. 타이머 완주 경로에서는 발생하지 않는다.
     */
    BelowManualSessionDuration,
    RecentEmergencyUnlock,
    WithinCooldown,
    AlreadyToday,
    WithinSameSession,
    AccessibilityOff,
    NotificationOff,
    NoRecentSuccess,
    NotHomeRoot,
    NoActivity,
    QuietHours,
    NoBackgroundingObserved,
}

sealed interface ReviewEligibilityDecision {
    data object Eligible : ReviewEligibilityDecision
    data class Ineligible(val reason: SkipReason) : ReviewEligibilityDecision
}
