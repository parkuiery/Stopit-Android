package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeAllowedAppCountBucket
import com.uiery.keep.analytics.AnalyticsParentModeDurationBucket
import com.uiery.keep.analytics.AnalyticsParentModeExtensionMinutesBucket
import com.uiery.keep.analytics.AnalyticsParentModePinResult

internal data class ParentModeSession(
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val durationMinutes: Int,
    val allowedApps: Set<String>,
    val state: ParentModeSessionState,
)

internal enum class ParentModeSessionState {
    Setup,
    Active,
    Expired,
    UnlockedByPin,
    Cancelled,
}

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
    data object PinRequired : ParentModeActionDecision

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
    ): ParentModeSessionState = when (session.state) {
        ParentModeSessionState.Active -> if (nowMillis >= session.expiresAtMillis) {
            ParentModeSessionState.Expired
        } else {
            ParentModeSessionState.Active
        }
        else -> session.state
    }

    fun shouldBlockPackage(
        session: ParentModeSession,
        packageName: String,
        nowMillis: Long,
    ): Boolean = when (resolveState(session, nowMillis)) {
        ParentModeSessionState.Active -> packageName !in session.allowedApps
        ParentModeSessionState.Expired -> true
        ParentModeSessionState.Setup,
        ParentModeSessionState.UnlockedByPin,
        ParentModeSessionState.Cancelled,
        -> false
    }

    fun requestParentAction(
        session: ParentModeSession,
        action: ParentModeParentAction,
        pinState: ParentModePinState,
        nowMillis: Long,
    ): ParentModeActionDecision {
        if (pinState != ParentModePinState.Verified) return ParentModeActionDecision.PinRequired

        return when (action) {
            ParentModeParentAction.EndNow -> ParentModeActionDecision.End(endedAtMillis = nowMillis)
            is ParentModeParentAction.Extend -> ParentModeActionDecision.Extend(
                expiresAtMillis = session.expiresAtMillis + action.extensionMinutes * MILLIS_PER_MINUTE,
                extensionMinutesBucket = extensionMinutesBucket(action.extensionMinutes),
            )
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
