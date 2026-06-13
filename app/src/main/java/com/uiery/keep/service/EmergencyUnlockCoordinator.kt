package com.uiery.keep.service

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsSnapshot
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal data class EmergencyUnlockAvailability(
    val enabled: Boolean,
    val dailyLimit: Int,
    val durationOptions: List<Int>,
    val reasonRequired: Boolean,
    val reason: EmergencyUnlockAvailabilityReason,
    val dailyLimitReached: Boolean,
    val dailyUnlockRemaining: Int,
)

enum class EmergencyUnlockAvailabilityReason {
    Available,
    Disabled,
    DailyLimitZero,
    DailyLimitExhausted,
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
    ) {
        internal suspend fun readAvailability(): EmergencyUnlockAvailability {
            val settings = readSettings()
            val usedCount = readUnlockCount(settings)
            return availability(settings = settings, usedUnlockCount = usedCount)
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
            if (!canCompleteEmergencyUnlockRequest(
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
                    availability = availability(settings = settings, usedUnlockCount = usedCount),
                )
            }

            val expireTime = nowMillis + durationMinutes * 60_000L
            val unlockCountRemaining = emergencyUnlockDailyRemaining(
                dailyLimit = settings.dailyLimit,
                todayUnlockCount = usedCount + 1,
            )
            val unlockData = EmergencyUnlockData(unlockedApps = apps, expireTimeMillis = expireTime)

            val historyId = repository.insert(
                EmergencyUnlockEntity(
                    timestamp = nowMillis,
                    reason = reason,
                    customReason = customReason,
                    unlockedApps = apps.toList(),
                    durationMinutes = durationMinutes,
                ),
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
            analytics.trackEmergencyUnlockCompleted(
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

        private fun availability(
            settings: EmergencyUnlockSettingsSnapshot,
            usedUnlockCount: Int,
        ): EmergencyUnlockAvailability {
            val reason = when {
                !settings.enabled -> EmergencyUnlockAvailabilityReason.Disabled
                settings.dailyLimit <= 0 -> EmergencyUnlockAvailabilityReason.DailyLimitZero
                isEmergencyUnlockDailyLimitReached(
                    dailyLimit = settings.dailyLimit,
                    todayUnlockCount = usedUnlockCount,
                ) -> EmergencyUnlockAvailabilityReason.DailyLimitExhausted
                else -> EmergencyUnlockAvailabilityReason.Available
            }
            return EmergencyUnlockAvailability(
                enabled = settings.enabled,
                dailyLimit = settings.dailyLimit,
                durationOptions = settings.durationOptions,
                reasonRequired = settings.reasonRequired,
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
