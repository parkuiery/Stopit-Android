package com.uiery.keep.service

import com.uiery.keep.analytics.EmergencyUnlockCompletionCoordinator
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.emergencyunlock.EmergencyUnlockRepository
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsSnapshot
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.domain.parentmode.ParentModeBlockReasonSource
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal data class EmergencyUnlockAvailability(
    val enabled: Boolean,
    val dailyLimit: Int,
    val durationOptions: List<Int>,
    val reasonRequired: Boolean,
    val countdownEnabled: Boolean,
    val countdownSeconds: Int,
    val reason: EmergencyUnlockAvailabilityReason,
    val dailyLimitReached: Boolean,
    val dailyUnlockRemaining: Int,
)

enum class EmergencyUnlockAvailabilityReason {
    Available,
    Disabled,
    DailyLimitZero,
    DailyLimitExhausted,

    /**
     * Parent mode is running, and it is the one lock the person holding the phone did not agree to.
     *
     * The self-control locks can be escaped by their own author because the blocker and the escapee
     * are the same person; that trade is theirs to make. Here they are not the same person, so a
     * button that opens everything three times a day would undo the PIN gate entirely. Placing a
     * call is exempt further up the block decision and does not depend on this.
     */
    ParentModeActive,
}

internal sealed interface EmergencyUnlockRequestResult {
    data class Completed(
        val source: String,
        val expireTimeMillis: Long,
        val dailyUnlockRemaining: Int,
        val stateSnapshot: EmergencyUnlockData,
    ) : EmergencyUnlockRequestResult

    data class Rejected(
        val availability: EmergencyUnlockAvailability,
    ) : EmergencyUnlockRequestResult
}

/**
 * Shared emergency-unlock orchestration for Block/Lock entry points.
 *
 * Pure policy decisions stay in [EmergencyUnlockPolicy.kt]. This coordinator owns the side-effect
 * order that must not drift between screens: settings read/sanitize, daily-limit lookup,
 * DataStore + Room persistence, analytics, and [EmergencyUnlockState] updates.
 */
class EmergencyUnlockCoordinator
    @Inject
    constructor(
        private val settingsStore: EmergencyUnlockSettingsStore,
        private val blockingStateStore: BlockingStateStore,
        private val repository: EmergencyUnlockRepository,
        private val analytics: KeepAnalytics,
        private val completionCoordinator: EmergencyUnlockCompletionCoordinator,
        private val parentModeBlockReasonSource: ParentModeBlockReasonSource,
    ) {
        internal suspend fun readAvailability(
            nowMillis: Long = System.currentTimeMillis(),
        ): EmergencyUnlockAvailability {
            val settings = readSettings()
            val usedCount = readUnlockCount(settings)
            return availability(
                settings = settings,
                usedUnlockCount = usedCount,
                parentModeActive = isParentModeBlocking(nowMillis),
            )
        }

        internal suspend fun markManualReset(nowMillis: Long = System.currentTimeMillis()) {
            settingsStore.markManualReset(nowMillis = nowMillis)
        }

        internal suspend fun completeUnlock(
            source: String,
            reason: String,
            customReason: String?,
            apps: Set<String>,
            durationMinutes: Int,
            nowMillis: Long = System.currentTimeMillis(),
        ): EmergencyUnlockRequestResult {
            val settings = readSettings()
            val usedCount = readUnlockCount(settings)
            // Re-checked here and not only in the UI: the sheet can have been opened a moment
            // before a parent mode session started, and EmergencyUnlockState carries an approved
            // window straight into it.
            val parentModeActive = isParentModeBlocking(nowMillis)
            if (parentModeActive ||
                !canCompleteEmergencyUnlockRequest(
                    settings = EmergencyUnlockSettings(
                        enabled = settings.enabled,
                        dailyLimit = settings.dailyLimit,
                        durationOptions = settings.durationOptions,
                        reasonRequired = settings.reasonRequired,
                    ),
                    todayUnlockCount = usedCount,
                    durationMinutes = durationMinutes,
                    reason = reason,
                )
            ) {
                return EmergencyUnlockRequestResult.Rejected(
                    availability = availability(
                        settings = settings,
                        usedUnlockCount = usedCount,
                        parentModeActive = parentModeActive,
                    ),
                )
            }

            val expireTime = nowMillis + durationMinutes * 60_000L
            val unlockCountRemaining = emergencyUnlockDailyRemaining(
                dailyLimit = settings.dailyLimit,
                todayUnlockCount = usedCount + 1,
            )
            val unlockData = EmergencyUnlockData(unlockedApps = apps, expireTimeMillis = expireTime)

            val historyId = repository.recordUnlock(
                timestamp = nowMillis,
                reason = reason,
                customReason = customReason,
                apps = apps,
                durationMinutes = durationMinutes,
            )
            try {
                blockingStateStore.saveEmergencyUnlockRuntimeState(apps = apps, expireTimeMillis = expireTime)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                runCatching { repository.deleteById(historyId) }
                    .onFailure { rollbackFailure -> failure.addSuppressed(rollbackFailure) }
                throw failure
            }
            EmergencyUnlockState.current = unlockData
            analytics.trackEmergencyUnlockUsed(
                source = source,
                unlockCountRemaining = unlockCountRemaining,
            )
            // 완료는 여기서 기록하지 않는다. 승인과 완료를 같은 자리에서 보내면 두 이벤트가
            // 항상 같은 수치가 되고, 완료율을 재려던 지표가 승인율을 재게 된다 (#1167).
            // payload 만 예약해두고 실제 기록은 창이 끝날 때 한다.
            completionCoordinator.reserve(
                reason = reason,
                durationMinutes = durationMinutes,
                remainingUnlocks = unlockCountRemaining,
            )

            return EmergencyUnlockRequestResult.Completed(
                source = source,
                expireTimeMillis = expireTime,
                dailyUnlockRemaining = unlockCountRemaining,
                stateSnapshot = unlockData,
            )
        }

        private suspend fun readSettings(): EmergencyUnlockSettingsSnapshot = settingsStore.readSettings()

        private suspend fun readUnlockCount(settings: EmergencyUnlockSettingsSnapshot): Int =
            if (settings.autoResetEnabled) {
                repository.countToday(todayStartMillis())
            } else {
                repository.countSince(settings.manualResetAtMillis)
            }

        private suspend fun isParentModeBlocking(nowMillis: Long): Boolean =
            parentModeBlockReasonSource.blockReason(nowMillis) != null

        private fun availability(
            settings: EmergencyUnlockSettingsSnapshot,
            usedUnlockCount: Int,
            parentModeActive: Boolean,
        ): EmergencyUnlockAvailability {
            val reason = when {
                // Ahead of the settings checks: turning emergency unlock on or raising the daily
                // limit must not read as a way through a lock somebody else set.
                parentModeActive -> EmergencyUnlockAvailabilityReason.ParentModeActive
                !settings.enabled -> EmergencyUnlockAvailabilityReason.Disabled
                settings.dailyLimit <= 0 -> EmergencyUnlockAvailabilityReason.DailyLimitZero
                isEmergencyUnlockDailyLimitReached(
                    dailyLimit = settings.dailyLimit,
                    todayUnlockCount = usedUnlockCount,
                ) -> EmergencyUnlockAvailabilityReason.DailyLimitExhausted
                else -> EmergencyUnlockAvailabilityReason.Available
            }
            return EmergencyUnlockAvailability(
                // Stays the user's setting. Whether the hatch can be used right now is [reason]
                // — folding parent mode in here would make one field mean two things.
                enabled = settings.enabled,
                dailyLimit = settings.dailyLimit,
                durationOptions = settings.durationOptions,
                reasonRequired = settings.reasonRequired,
                countdownEnabled = settings.countdownEnabled,
                countdownSeconds = settings.countdownSeconds,
                reason = reason,
                dailyLimitReached = reason == EmergencyUnlockAvailabilityReason.DailyLimitExhausted,
                dailyUnlockRemaining = emergencyUnlockDailyRemaining(
                    dailyLimit = settings.dailyLimit,
                    todayUnlockCount = usedUnlockCount,
                ),
            )
        }

        private fun todayStartMillis(): Long {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }
    }
