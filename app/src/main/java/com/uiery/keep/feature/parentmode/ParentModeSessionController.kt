package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeEndReason
import com.uiery.keep.analytics.KeepAnalytics
import javax.inject.Inject

internal class ParentModeSessionController @Inject constructor(
    private val store: ParentModeSessionStore,
    private val analytics: KeepAnalytics,
) {
    suspend fun start(
        durationMinutes: Int,
        allowedApps: Set<String>,
        pinState: ParentModePinState,
        nowMillis: Long,
    ): ParentModeSessionControllerResult {
        val validation = ParentModePolicy.validateSetup(
            durationMinutes = durationMinutes,
            allowedAppCount = allowedApps.size,
            pinState = pinState,
        )
        if (!validation.canStart) {
            return ParentModeSessionControllerResult.SetupBlocked(validation.issues)
        }

        val session = ParentModePolicy.startSession(
            startedAtMillis = nowMillis,
            durationMinutes = durationMinutes,
            allowedApps = allowedApps,
        )
        store.save(session)

        val durationBucket = ParentModePolicy.durationBucket(durationMinutes)
        val allowedAppCountBucket = ParentModePolicy.allowedAppCountBucket(allowedApps.size)
        analytics.trackParentModeDurationSelected(durationBucket)
        analytics.trackParentModeAllowedAppsSelected(allowedAppCountBucket)
        analytics.trackParentModeStarted(
            durationMinutesBucket = durationBucket,
            allowedAppCountBucket = allowedAppCountBucket,
        )

        return ParentModeSessionControllerResult.Started(session)
    }

    suspend fun extend(
        extensionMinutes: Int,
        pinState: ParentModePinState,
        nowMillis: Long,
    ): ParentModeSessionControllerResult {
        val session = store.read() ?: return ParentModeSessionControllerResult.NoActiveSession
        val decision = ParentModePolicy.requestParentAction(
            session = session,
            action = ParentModeParentAction.Extend(extensionMinutes),
            pinState = pinState,
            nowMillis = nowMillis,
        )
        return when (decision) {
            ParentModeActionDecision.InvalidExtension -> ParentModeSessionControllerResult.InvalidExtension
            is ParentModeActionDecision.Extend -> {
                val updatedSession = session.copy(
                    expiresAtMillis = decision.expiresAtMillis,
                    durationMinutes = session.durationMinutes + extensionMinutes,
                    state = ParentModeSessionState.Active,
                )
                store.save(updatedSession)
                analytics.trackParentModeExtended(decision.extensionMinutesBucket)
                ParentModeSessionControllerResult.Extended(updatedSession)
            }
            is ParentModeActionDecision.End -> ParentModeSessionControllerResult.Ended(
                session.copy(
                    expiresAtMillis = decision.endedAtMillis,
                    state = ParentModeSessionState.UnlockedByPin,
                ),
            )
            ParentModeActionDecision.PinRequired -> ParentModeSessionControllerResult.PinRequired
        }
    }

    suspend fun endNow(
        pinState: ParentModePinState,
        nowMillis: Long,
    ): ParentModeSessionControllerResult {
        val session = store.read() ?: return ParentModeSessionControllerResult.NoActiveSession
        val decision = ParentModePolicy.requestParentAction(
            session = session,
            action = ParentModeParentAction.EndNow,
            pinState = pinState,
            nowMillis = nowMillis,
        )
        return when (decision) {
            is ParentModeActionDecision.End -> {
                val endedSession = session.copy(
                    expiresAtMillis = decision.endedAtMillis,
                    state = ParentModeSessionState.UnlockedByPin,
                )
                store.save(endedSession)
                analytics.trackParentModeUnlockedByPin(
                    pinResult = ParentModePolicy.pinResult(pinState),
                    endReason = AnalyticsParentModeEndReason.PIN_UNLOCKED,
                )
                analytics.trackParentModeCompleted(
                    durationMinutesBucket = ParentModePolicy.durationBucket(session.durationMinutes),
                    endReason = AnalyticsParentModeEndReason.PIN_UNLOCKED,
                )
                ParentModeSessionControllerResult.Ended(endedSession)
            }
            is ParentModeActionDecision.Extend -> ParentModeSessionControllerResult.Extended(
                session.copy(
                    expiresAtMillis = decision.expiresAtMillis,
                    durationMinutes = session.durationMinutes,
                    state = ParentModeSessionState.Active,
                ),
            )
            ParentModeActionDecision.InvalidExtension -> ParentModeSessionControllerResult.InvalidExtension
            ParentModeActionDecision.PinRequired -> ParentModeSessionControllerResult.PinRequired
        }
    }
}

internal sealed interface ParentModeSessionControllerResult {
    data class SetupBlocked(
        val issues: Set<ParentModeSetupIssue>,
    ) : ParentModeSessionControllerResult

    data class Started(
        val session: ParentModeSession,
    ) : ParentModeSessionControllerResult

    data class Extended(
        val session: ParentModeSession,
    ) : ParentModeSessionControllerResult

    data class Ended(
        val session: ParentModeSession,
    ) : ParentModeSessionControllerResult

    data object InvalidExtension : ParentModeSessionControllerResult

    data object PinRequired : ParentModeSessionControllerResult

    data object NoActiveSession : ParentModeSessionControllerResult
}
