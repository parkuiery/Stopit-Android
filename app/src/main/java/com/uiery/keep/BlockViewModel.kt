package com.uiery.keep

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EmergencyUnlockData
import com.uiery.keep.service.EmergencyUnlockSettings
import com.uiery.keep.service.EmergencyUnlockState
import com.uiery.keep.service.canCompleteEmergencyUnlockRequest
import com.uiery.keep.service.emergencyUnlockDailyRemaining
import com.uiery.keep.service.isEmergencyUnlockAvailable
import com.uiery.keep.service.sanitizeEmergencyUnlockDailyLimit
import com.uiery.keep.service.sanitizeEmergencyUnlockDurationOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BlockViewModel
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val emergencyUnlockDao: EmergencyUnlockDao,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<BlockUiState, BlockSideEffect> {
        override val container: Container<BlockUiState, BlockSideEffect> = container(BlockUiState())

        init {
            analytics.logScreenView(KeepAnalyticsScreen.BLOCK)
            checkDailyLimit()
        }

        internal fun trackBlockShown(
            packageName: String,
            blockSource: String,
            routineId: String?,
        ) = intent {
            val firstOpenTimestamp =
                dataStore.data
                    .map { preferences -> preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] }
                    .firstOrNull()
                    ?: System.currentTimeMillis()
            val elapsedSeconds =
                TimeUnit.MILLISECONDS
                    .toSeconds(System.currentTimeMillis() - firstOpenTimestamp)
                    .coerceAtLeast(0L)
            val hasTrackedFirstCoreAction =
                dataStore.data
                    .map { preferences -> preferences[PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION] == true }
                    .firstOrNull() == true

            analytics.trackAppBlockIntercepted(
                blockSource = blockSource,
                blockedAppPackage = packageName,
            )
            if (hasTrackedFirstCoreAction) {
                analytics.trackCoreActionCompleted(
                    elapsedSinceFirstOpenSeconds = elapsedSeconds,
                    blockingMode = blockSource,
                    blockedAppPackage = packageName,
                    routineId = routineId,
                )
            } else {
                analytics.trackFirstCoreActionCompleted(
                    elapsedSinceFirstOpenSeconds = elapsedSeconds,
                    blockingMode = blockSource,
                    blockedAppPackage = packageName,
                    routineId = routineId,
                )
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION] = true
                    if (preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] == null) {
                        preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] = firstOpenTimestamp
                    }
                }
            }
        }

        private suspend fun readEmergencyUnlockSettings(): EmergencyUnlockSettings {
            val preferences = dataStore.data.firstOrNull()
            return EmergencyUnlockSettings(
                enabled = preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_ENABLED) ?: true,
                dailyLimit = sanitizeEmergencyUnlockDailyLimit(
                    preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT),
                ),
                durationOptions = sanitizeEmergencyUnlockDurationOptions(
                    preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS),
                ),
                reasonRequired = preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED) ?: true,
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

        private fun checkDailyLimit() = intent {
            val settings = readEmergencyUnlockSettings()
            val count = emergencyUnlockDao.countToday(todayStartMillis())
            reduce {
                state.copy(
                    emergencyUnlockEnabled = settings.enabled,
                    emergencyUnlockDailyLimit = settings.dailyLimit,
                    emergencyUnlockDurationOptions = settings.durationOptions,
                    emergencyUnlockReasonRequired = settings.reasonRequired,
                    dailyLimitReached = !isEmergencyUnlockAvailable(
                        enabled = settings.enabled,
                        dailyLimit = settings.dailyLimit,
                        todayUnlockCount = count,
                    ),
                    dailyUnlockRemaining = emergencyUnlockDailyRemaining(
                        dailyLimit = settings.dailyLimit,
                        todayUnlockCount = count,
                    ),
                )
            }
        }

        internal fun showEmergencyUnlockSheet() = intent {
            reduce { state.copy(isShowEmergencyUnlockSheet = true) }
        }

        internal fun hideEmergencyUnlockSheet() = intent {
            reduce { state.copy(isShowEmergencyUnlockSheet = false) }
        }

        internal fun emergencyUnlock(
            reason: String,
            customReason: String?,
            apps: Set<String>,
            durationMinutes: Int,
        ) = intent {
            val now = System.currentTimeMillis()
            val settings = readEmergencyUnlockSettings()
            val todayCount = emergencyUnlockDao.countToday(todayStartMillis())
            if (!canCompleteEmergencyUnlockRequest(
                    settings = settings,
                    todayUnlockCount = todayCount,
                    durationMinutes = durationMinutes,
                    reason = reason,
                )
            ) {
                checkDailyLimit()
                return@intent
            }
            val expireTime = now + durationMinutes * 60_000L
            val unlockCountRemaining = emergencyUnlockDailyRemaining(
                dailyLimit = settings.dailyLimit,
                todayUnlockCount = todayCount + 1,
            )

            EmergencyUnlockState.current = EmergencyUnlockData(
                unlockedApps = apps,
                expireTimeMillis = expireTime,
            )

            dataStore.edit { preferences ->
                preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = apps
                preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTime
            }

            emergencyUnlockDao.insert(
                EmergencyUnlockEntity(
                    timestamp = now,
                    reason = reason,
                    customReason = customReason,
                    unlockedApps = apps.toList(),
                    durationMinutes = durationMinutes,
                )
            )

            analytics.trackEmergencyUnlockUsed(
                source = AnalyticsSource.BLOCK_SCREEN,
                unlockCountRemaining = unlockCountRemaining,
            )
            analytics.trackEmergencyUnlockCompleted(
                reason = reason,
                durationMinutes = durationMinutes,
                remainingUnlocks = unlockCountRemaining,
            )

            checkDailyLimit()
            postSideEffect(BlockSideEffect.UnlockCompleted)
        }
    }

data class BlockUiState(
    val isShowEmergencyUnlockSheet: Boolean = false,
    val dailyLimitReached: Boolean = false,
    val dailyUnlockRemaining: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val emergencyUnlockEnabled: Boolean = true,
    val emergencyUnlockDailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val emergencyUnlockDurationOptions: List<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS,
    val emergencyUnlockReasonRequired: Boolean = true,
)

sealed class BlockSideEffect {
    data object UnlockCompleted : BlockSideEffect()
}

internal fun String?.orDefaultBlockSource(): String =
    when (this) {
        AnalyticsBlockSource.MANUAL_KEEP,
        AnalyticsBlockSource.TIMED_LOCK,
        AnalyticsBlockSource.ROUTINE -> this
        else -> AnalyticsBlockSource.MANUAL_KEEP
    }
