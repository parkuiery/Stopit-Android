package com.uiery.keep.ui.component

import com.uiery.keep.R
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockStepName
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockValidationReason
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EMERGENCY_UNLOCK_REASON_NOT_REQUIRED

internal enum class EmergencyUnlockBottomSheetStep {
    REASON,
    APPS,
    DURATION,
    COUNTDOWN,
}

internal enum class EmergencyUnlockBottomSheetEffect {
    None,
    SubmitUnlock,
    Dismiss,
}

internal data class EmergencyUnlockBottomSheetRequest(
    val reason: String,
    val customReason: String?,
    val apps: Set<String>,
    val durationMinutes: Int,
)

internal data class EmergencyUnlockBottomSheetTransition(
    val state: EmergencyUnlockBottomSheetState,
    val effect: EmergencyUnlockBottomSheetEffect,
)

internal data class EmergencyUnlockBottomSheetState(
    val blockedApps: Set<String>,
    val durationOptions: List<Int>,
    val reasonStepEnabled: Boolean,
    val step: EmergencyUnlockBottomSheetStep,
    val selectedReason: String? = null,
    val customReason: String = "",
    val selectedApps: Set<String> = emptySet(),
    val selectedDurationMinutes: Int,
    val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
) {
    val visibleSteps: List<EmergencyUnlockBottomSheetStep> = if (reasonStepEnabled) {
        listOf(
            EmergencyUnlockBottomSheetStep.REASON,
            EmergencyUnlockBottomSheetStep.APPS,
            EmergencyUnlockBottomSheetStep.DURATION,
        )
    } else {
        listOf(
            EmergencyUnlockBottomSheetStep.APPS,
            EmergencyUnlockBottomSheetStep.DURATION,
        )
    }

    val canContinueFromReason: Boolean
        get() = selectedReason != null && (selectedReason != OTHER_REASON_KEY || customReason.isNotBlank())

    val canContinueFromApps: Boolean
        get() = selectedApps.isNotEmpty()

    val stepPurposeTextRes: Int?
        get() = when (step) {
            EmergencyUnlockBottomSheetStep.REASON -> R.string.emergency_unlock_reason_step_purpose
            EmergencyUnlockBottomSheetStep.APPS -> R.string.emergency_unlock_apps_step_purpose
            EmergencyUnlockBottomSheetStep.DURATION -> R.string.emergency_unlock_duration_step_purpose
            EmergencyUnlockBottomSheetStep.COUNTDOWN -> null
        }

    val stepHelperTextRes: Int?
        get() = when (step) {
            EmergencyUnlockBottomSheetStep.REASON -> R.string.emergency_unlock_reason_helper
            EmergencyUnlockBottomSheetStep.APPS -> if (reasonStepEnabled) {
                R.string.emergency_unlock_apps_helper
            } else {
                R.string.emergency_unlock_apps_no_reason_helper
            }
            EmergencyUnlockBottomSheetStep.DURATION -> R.string.emergency_unlock_duration_helper
            EmergencyUnlockBottomSheetStep.COUNTDOWN -> null
        }

    val validationHelperTextRes: Int?
        get() = when (step) {
            EmergencyUnlockBottomSheetStep.REASON -> when {
                selectedReason == OTHER_REASON_KEY && customReason.isBlank() ->
                    R.string.emergency_unlock_reason_other_required_helper
                selectedReason == null -> R.string.emergency_unlock_reason_required_helper
                else -> null
            }
            EmergencyUnlockBottomSheetStep.APPS -> if (selectedApps.isEmpty()) {
                R.string.emergency_unlock_apps_required_helper
            } else {
                null
            }
            EmergencyUnlockBottomSheetStep.DURATION -> null
            EmergencyUnlockBottomSheetStep.COUNTDOWN -> null
        }

    val validationReason: String?
        get() = when (step) {
            EmergencyUnlockBottomSheetStep.REASON -> when {
                selectedReason == OTHER_REASON_KEY && customReason.isBlank() ->
                    AnalyticsEmergencyUnlockValidationReason.MISSING_CUSTOM_REASON
                selectedReason == null -> AnalyticsEmergencyUnlockValidationReason.MISSING_REASON
                else -> null
            }
            EmergencyUnlockBottomSheetStep.APPS -> if (selectedApps.isEmpty()) {
                AnalyticsEmergencyUnlockValidationReason.MISSING_APP_SELECTION
            } else {
                null
            }
            EmergencyUnlockBottomSheetStep.DURATION,
            EmergencyUnlockBottomSheetStep.COUNTDOWN,
            -> null
        }

    val analyticsStepName: String
        get() = when (step) {
            EmergencyUnlockBottomSheetStep.REASON -> AnalyticsEmergencyUnlockStepName.REASON
            EmergencyUnlockBottomSheetStep.APPS -> AnalyticsEmergencyUnlockStepName.APPS
            EmergencyUnlockBottomSheetStep.DURATION -> AnalyticsEmergencyUnlockStepName.DURATION
            EmergencyUnlockBottomSheetStep.COUNTDOWN -> AnalyticsEmergencyUnlockStepName.COUNTDOWN
        }

    val selectedReasonReflectionTextRes: Int?
        get() = when (selectedReason) {
            "work" -> R.string.emergency_unlock_reason_work_reflection
            "contact" -> R.string.emergency_unlock_reason_contact_reflection
            "info" -> R.string.emergency_unlock_reason_info_reflection
            "habit" -> R.string.emergency_unlock_reason_habit_reflection
            "boredom" -> R.string.emergency_unlock_reason_boredom_reflection
            OTHER_REASON_KEY -> R.string.emergency_unlock_reason_other_reflection
            else -> null
        }

    fun selectReason(reason: String): EmergencyUnlockBottomSheetState = copy(selectedReason = reason)

    fun changeCustomReason(reason: String): EmergencyUnlockBottomSheetState = copy(customReason = reason)

    fun toggleApp(packageName: String): EmergencyUnlockBottomSheetState {
        if (!blockedApps.contains(packageName)) return this
        val nextApps = if (selectedApps.contains(packageName)) {
            selectedApps - packageName
        } else {
            selectedApps + packageName
        }
        return copy(selectedApps = nextApps)
    }

    fun selectApps(apps: Set<String>): EmergencyUnlockBottomSheetState = copy(
        selectedApps = apps.intersect(blockedApps),
    )

    fun selectDuration(minutes: Int): EmergencyUnlockBottomSheetState = copy(
        selectedDurationMinutes = minutes,
    )

    fun goNext(): EmergencyUnlockBottomSheetState = when (step) {
        EmergencyUnlockBottomSheetStep.REASON -> if (canContinueFromReason) {
            copy(step = EmergencyUnlockBottomSheetStep.APPS)
        } else {
            this
        }
        EmergencyUnlockBottomSheetStep.APPS -> if (canContinueFromApps) {
            copy(step = EmergencyUnlockBottomSheetStep.DURATION)
        } else {
            this
        }
        EmergencyUnlockBottomSheetStep.DURATION -> copy(step = EmergencyUnlockBottomSheetStep.COUNTDOWN)
        EmergencyUnlockBottomSheetStep.COUNTDOWN -> this
    }

    fun countdownTick(): EmergencyUnlockBottomSheetTransition {
        val nextState = copy(countdownSeconds = (countdownSeconds - 1).coerceAtLeast(0))
        val effect = if (nextState.countdownSeconds == 0) {
            EmergencyUnlockBottomSheetEffect.SubmitUnlock
        } else {
            EmergencyUnlockBottomSheetEffect.None
        }
        return EmergencyUnlockBottomSheetTransition(nextState, effect)
    }

    fun cancelCountdown(): EmergencyUnlockBottomSheetTransition = EmergencyUnlockBottomSheetTransition(
        state = this,
        effect = EmergencyUnlockBottomSheetEffect.Dismiss,
    )

    fun toUnlockRequest(): EmergencyUnlockBottomSheetRequest = requireNotNull(toUnlockRequestOrNull()) {
        "Emergency unlock request requires countdown step and selected apps"
    }

    fun toUnlockRequestOrNull(): EmergencyUnlockBottomSheetRequest? {
        if (step != EmergencyUnlockBottomSheetStep.COUNTDOWN || selectedApps.isEmpty()) return null
        return EmergencyUnlockBottomSheetRequest(
            reason = if (reasonStepEnabled) selectedReason.orEmpty() else EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
            customReason = if (reasonStepEnabled && selectedReason == OTHER_REASON_KEY) customReason else null,
            apps = selectedApps,
            durationMinutes = selectedDurationMinutes,
        )
    }

    companion object {
        private const val DEFAULT_COUNTDOWN_SECONDS = 30
        private const val OTHER_REASON_KEY = "other"

        fun initial(
            blockedApps: Set<String>,
            durationOptions: List<Int>,
            reasonStepEnabled: Boolean,
        ): EmergencyUnlockBottomSheetState {
            val safeDurationOptions = durationOptions.ifEmpty { DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS }
            return EmergencyUnlockBottomSheetState(
                blockedApps = blockedApps,
                durationOptions = safeDurationOptions,
                reasonStepEnabled = reasonStepEnabled,
                step = if (reasonStepEnabled) {
                    EmergencyUnlockBottomSheetStep.REASON
                } else {
                    EmergencyUnlockBottomSheetStep.APPS
                },
                selectedDurationMinutes = safeDurationOptions.first(),
            )
        }
    }
}
