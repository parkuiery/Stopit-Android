package com.uiery.keep.service

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsSnapshot
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import java.util.Calendar
import javax.inject.Inject

internal data class EmergencyUnlockAvailability(
    val enabled: Boolean,
    val dailyLimit: Int,
    val durationOptions: List<Int>,
    val reasonRequired: Boolean,
    val dailyLimitReached: Boolean,
    val dailyUnlockRemaining: Int,
)

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
        private val emergencyUnlockDao: EmergencyUnlockDao,
        private val analytics: KeepAnalytics,
    ) {
        internal suspend fun readAvailability(): EmergencyUnlockAvailability {
            val settings = readSettings()
            val todayCount = emergencyUnlockDao.countToday(todayStartMillis())
            return availability(settings = settings, todayUnlockCount = todayCount)
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
            val todayCount = emergencyUnlockDao.countToday(todayStartMillis())
            if (!canCompleteEmergencyUnlockRequest(
                    settings = EmergencyUnlockSettings(
                        enabled = settings.enabled,
                        dailyLimit = settings.dailyLimit,
                        durationOptions = settings.durationOptions,
                        reasonRequired = settings.reasonRequired,
                    ),
                    todayUnlockCount = todayCount,
                    durationMinutes = durationMinutes,
                    reason = reason,
                )
            ) {
                return EmergencyUnlockRequestResult.Rejected(
                    availability = availability(settings = settings, todayUnlockCount = todayCount),
                )
            }

            val expireTime = nowMillis + durationMinutes * 60_000L
            val unlockCountRemaining = emergencyUnlockDailyRemaining(
                dailyLimit = settings.dailyLimit,
                todayUnlockCount = todayCount + 1,
            )
            val unlockData = EmergencyUnlockData(unlockedApps = apps, expireTimeMillis = expireTime)

            EmergencyUnlockState.current = unlockData
            blockingStateStore.saveEmergencyUnlockRuntimeState(apps = apps, expireTimeMillis = expireTime)
            emergencyUnlockDao.insert(
                EmergencyUnlockEntity(
                    timestamp = nowMillis,
                    reason = reason,
                    customReason = customReason,
                    unlockedApps = apps.toList(),
                    durationMinutes = durationMinutes,
                ),
            )
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

        private fun availability(
            settings: EmergencyUnlockSettingsSnapshot,
            todayUnlockCount: Int,
        ): EmergencyUnlockAvailability =
            EmergencyUnlockAvailability(
                enabled = settings.enabled,
                dailyLimit = settings.dailyLimit,
                durationOptions = settings.durationOptions,
                reasonRequired = settings.reasonRequired,
                dailyLimitReached = !isEmergencyUnlockAvailable(
                    enabled = settings.enabled,
                    dailyLimit = settings.dailyLimit,
                    todayUnlockCount = todayUnlockCount,
                ),
                dailyUnlockRemaining = emergencyUnlockDailyRemaining(
                    dailyLimit = settings.dailyLimit,
                    todayUnlockCount = todayUnlockCount,
                ),
            )

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
