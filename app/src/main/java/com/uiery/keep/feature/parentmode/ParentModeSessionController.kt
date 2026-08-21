package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeEndReason
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.parentmode.ParentModeSessionStore
import com.uiery.keep.domain.parentmode.ParentModeRuntimePolicy
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import javax.inject.Inject

internal class ParentModeSessionController @Inject constructor(
    private val store: ParentModeSessionStore,
    private val analytics: KeepAnalytics,
) {
    /**
     * Takes the typed PIN rather than a verdict about it, because this is the one call that has to
     * turn it into something storable. The plaintext stops here: only the salted digest reaches
     * [ParentModeSessionStore], and nothing carries it into analytics.
     */
    suspend fun start(
        durationMinutes: Int,
        allowedApps: Set<String>,
        guardianPin: String,
        guardianPinConfirmation: String,
        nowMillis: Long,
    ): ParentModeSessionControllerResult {
        val pinState = ParentModePolicy.setupPinState(
            pin = guardianPin,
            confirmation = guardianPinConfirmation,
        )
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
            guardianPin = ParentModePolicy.digestGuardianPin(guardianPin),
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
        pinAttempt: String,
        nowMillis: Long,
    ): ParentModeSessionControllerResult {
        val session = store.read() ?: return ParentModeSessionControllerResult.NoActiveSession
        if (session.state != ParentModeSessionState.Active) {
            return ParentModeSessionControllerResult.NoStateChange(session)
        }
        val decision = ParentModePolicy.requestParentAction(
            session = session,
            action = ParentModeParentAction.Extend(extensionMinutes),
            pinVerdict = ParentModePolicy.verifyGuardianPin(session = session, pinAttempt = pinAttempt),
            nowMillis = nowMillis,
        )
        return when (decision) {
            ParentModeActionDecision.Expired -> persistExpiredSession(session)
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
        pinAttempt: String,
        nowMillis: Long,
    ): ParentModeSessionControllerResult {
        val session = store.read() ?: return ParentModeSessionControllerResult.NoActiveSession
        if (session.state != ParentModeSessionState.Active) {
            return ParentModeSessionControllerResult.NoStateChange(session)
        }
        val pinVerdict = ParentModePolicy.verifyGuardianPin(session = session, pinAttempt = pinAttempt)
        val decision = ParentModePolicy.requestParentAction(
            session = session,
            action = ParentModeParentAction.EndNow,
            pinVerdict = pinVerdict,
            nowMillis = nowMillis,
        )
        return when (decision) {
            ParentModeActionDecision.Expired -> persistExpiredSession(session)
            is ParentModeActionDecision.End -> {
                val endedSession = session.copy(
                    expiresAtMillis = decision.endedAtMillis,
                    state = ParentModeSessionState.UnlockedByPin,
                )
                store.save(endedSession)
                // FAILURE stays unreachable here by design — a failed PIN unlocks nothing, so it
                // has no place on an "unlocked by pin" event. NOT_CONFIGURED is newly reachable:
                // it is a session that predates the stored digest ending without a PIN gate.
                analytics.trackParentModeUnlockedByPin(
                    pinResult = ParentModePolicy.pinResult(ParentModePolicy.pinState(pinVerdict)),
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

    suspend fun markExpiredIfNeeded(nowMillis: Long): ParentModeSessionControllerResult {
        val session = store.read() ?: return ParentModeSessionControllerResult.NoActiveSession
        if (session.state != ParentModeSessionState.Active) {
            return ParentModeSessionControllerResult.NoStateChange(session)
        }
        if (ParentModeRuntimePolicy.resolveState(session, nowMillis) != ParentModeSessionState.Expired) {
            return ParentModeSessionControllerResult.NoStateChange(session)
        }

        return persistExpiredSession(session)
    }

    suspend fun clearFinishedSession(): ParentModeSessionControllerResult {
        val session = store.read() ?: return ParentModeSessionControllerResult.NoActiveSession
        if (session.state == ParentModeSessionState.Active) {
            return ParentModeSessionControllerResult.NoStateChange(session)
        }
        store.clear()
        return ParentModeSessionControllerResult.Cleared
    }

    private suspend fun persistExpiredSession(session: ParentModeSession): ParentModeSessionControllerResult.Expired {
        val expiredSession = session.copy(state = ParentModeSessionState.Expired)
        store.save(expiredSession)
        analytics.trackParentModeCompleted(
            durationMinutesBucket = ParentModePolicy.durationBucket(session.durationMinutes),
            endReason = AnalyticsParentModeEndReason.TIME_EXPIRED,
        )
        return ParentModeSessionControllerResult.Expired(expiredSession)
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

    data class Expired(
        val session: ParentModeSession,
    ) : ParentModeSessionControllerResult

    data class NoStateChange(
        val session: ParentModeSession,
    ) : ParentModeSessionControllerResult

    data object InvalidExtension : ParentModeSessionControllerResult

    data object Cleared : ParentModeSessionControllerResult

    data object PinRequired : ParentModeSessionControllerResult

    data object NoActiveSession : ParentModeSessionControllerResult
}
