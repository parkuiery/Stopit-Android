package com.uiery.keep.domain.pomodoro

import com.uiery.keep.analytics.KeepAnalytics

/**
 * 뽀모도로 이벤트만 받아 적는 테스트용 analytics.
 *
 * 값을 `|` 로 이어 붙인 문자열로 모아 두면 순서와 payload 를 한 줄로 단언할 수 있고, payload 에
 * 원문 값이 섞여 들어가는 회귀도 그대로 드러난다.
 */
internal class RecordingPomodoroAnalytics : KeepAnalytics {
    val started = mutableListOf<String>()
    val focusCompleted = mutableListOf<String>()
    val breakStarted = mutableListOf<String>()
    val ended = mutableListOf<String>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit

    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit

    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit

    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit

    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackPomodoroSessionStarted(
        preset: String,
        entrySurface: String,
        selectedAppCountBucket: String,
        focusMinutesBucket: String,
        cycleCountBucket: String,
    ) {
        started += "$preset|$entrySurface|$selectedAppCountBucket|$focusMinutesBucket|$cycleCountBucket"
    }

    override fun trackPomodoroFocusCompleted(preset: String, cycleIndexBucket: String) {
        focusCompleted += "$preset|$cycleIndexBucket"
    }

    override fun trackPomodoroBreakStarted(preset: String, breakType: String) {
        breakStarted += "$preset|$breakType"
    }

    override fun trackPomodoroSessionEnded(
        preset: String,
        endReason: String,
        completedFocusCountBucket: String,
        elapsedMinutesBucket: String,
    ) {
        ended += "$preset|$endReason|$completedFocusCountBucket|$elapsedMinutesBucket"
    }
}
