package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeAllowedAppCountBucket
import com.uiery.keep.analytics.AnalyticsParentModeDurationBucket
import com.uiery.keep.analytics.AnalyticsParentModeExtensionMinutesBucket
import com.uiery.keep.analytics.AnalyticsParentModePinResult
import com.uiery.keep.domain.parentmode.ParentModeRuntimePolicy
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState

internal enum class ParentModePinState {
    NotConfigured,
    Verified,
    Failed,
}

internal enum class ParentModeSetupIssue {
    InvalidDuration,
    NoAllowedApps,
    PinNotVerified,
}

internal data class ParentModeSetupValidation(
    val issues: Set<ParentModeSetupIssue>,
) {
    val canStart: Boolean = issues.isEmpty()
}

internal sealed interface ParentModeParentAction {
    data object EndNow : ParentModeParentAction

    data class Extend(
        val extensionMinutes: Int,
    ) : ParentModeParentAction
}

internal sealed interface ParentModeActionDecision {
    data object Expired : ParentModeActionDecision

    data object PinRequired : ParentModeActionDecision

    data object InvalidExtension : ParentModeActionDecision

    data class End(
        val endedAtMillis: Long,
    ) : ParentModeActionDecision

    data class Extend(
        val expiresAtMillis: Long,
        val extensionMinutesBucket: String,
    ) : ParentModeActionDecision
}

internal object ParentModePolicy {
    fun validateSetup(
        durationMinutes: Int,
        allowedAppCount: Int,
        pinState: ParentModePinState,
    ): ParentModeSetupValidation {
        val issues = buildSet {
            if (durationMinutes <= 0) add(ParentModeSetupIssue.InvalidDuration)
            if (allowedAppCount <= 0) add(ParentModeSetupIssue.NoAllowedApps)
            if (pinState != ParentModePinState.Verified) add(ParentModeSetupIssue.PinNotVerified)
        }
        return ParentModeSetupValidation(issues)
    }

    fun startSession(
        startedAtMillis: Long,
        durationMinutes: Int,
        allowedApps: Set<String>,
    ): ParentModeSession = ParentModeSession(
        startedAtMillis = startedAtMillis,
        expiresAtMillis = startedAtMillis + durationMinutes * MILLIS_PER_MINUTE,
        durationMinutes = durationMinutes,
        allowedApps = allowedApps,
        state = ParentModeSessionState.Active,
    )

    fun durationBucket(durationMinutes: Int): String = when {
        durationMinutes <= 9 -> AnalyticsParentModeDurationBucket.ONE_TO_NINE
        durationMinutes == 10 -> AnalyticsParentModeDurationBucket.TEN
        durationMinutes <= 20 -> AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY
        durationMinutes <= 30 -> AnalyticsParentModeDurationBucket.TWENTY_ONE_TO_THIRTY
        durationMinutes <= 60 -> AnalyticsParentModeDurationBucket.THIRTY_ONE_TO_SIXTY
        else -> AnalyticsParentModeDurationBucket.SIXTY_ONE_PLUS
    }

    fun extensionMinutesBucket(extensionMinutes: Int): String = when {
        extensionMinutes <= 9 -> AnalyticsParentModeExtensionMinutesBucket.ONE_TO_NINE
        extensionMinutes == 10 -> AnalyticsParentModeExtensionMinutesBucket.TEN
        extensionMinutes <= 20 -> AnalyticsParentModeExtensionMinutesBucket.ELEVEN_TO_TWENTY
        extensionMinutes <= 30 -> AnalyticsParentModeExtensionMinutesBucket.TWENTY_ONE_TO_THIRTY
        else -> AnalyticsParentModeExtensionMinutesBucket.THIRTY_ONE_PLUS
    }

    fun allowedAppCountBucket(allowedAppCount: Int): String = when {
        allowedAppCount <= 1 -> AnalyticsParentModeAllowedAppCountBucket.ONE
        allowedAppCount <= 3 -> AnalyticsParentModeAllowedAppCountBucket.TWO_TO_THREE
        allowedAppCount <= 6 -> AnalyticsParentModeAllowedAppCountBucket.FOUR_TO_SIX
        else -> AnalyticsParentModeAllowedAppCountBucket.SEVEN_PLUS
    }

    fun pinResult(pinState: ParentModePinState): String = when (pinState) {
        ParentModePinState.Verified -> AnalyticsParentModePinResult.SUCCESS
        ParentModePinState.Failed -> AnalyticsParentModePinResult.FAILURE
        ParentModePinState.NotConfigured -> AnalyticsParentModePinResult.NOT_CONFIGURED
    }

    fun resolveState(
        session: ParentModeSession,
        nowMillis: Long,
    ): ParentModeSessionState = ParentModeRuntimePolicy.resolveState(session, nowMillis)

    fun shouldBlockPackage(
        session: ParentModeSession,
        packageName: String,
        nowMillis: Long,
    ): Boolean = ParentModeRuntimePolicy.shouldBlockPackage(
        session = session,
        packageName = packageName,
        nowMillis = nowMillis,
    )

    fun requestParentAction(
        session: ParentModeSession,
        action: ParentModeParentAction,
        pinState: ParentModePinState,
        nowMillis: Long,
    ): ParentModeActionDecision {
        if (resolveState(session, nowMillis) == ParentModeSessionState.Expired) {
            return ParentModeActionDecision.Expired
        }
        if (pinState != ParentModePinState.Verified) return ParentModeActionDecision.PinRequired

        return when (action) {
            ParentModeParentAction.EndNow -> ParentModeActionDecision.End(endedAtMillis = nowMillis)
            is ParentModeParentAction.Extend -> {
                if (action.extensionMinutes <= 0) {
                    ParentModeActionDecision.InvalidExtension
                } else {
                    ParentModeActionDecision.Extend(
                        expiresAtMillis = session.expiresAtMillis + action.extensionMinutes * MILLIS_PER_MINUTE,
                        extensionMinutesBucket = extensionMinutesBucket(action.extensionMinutes),
                    )
                }
            }
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
