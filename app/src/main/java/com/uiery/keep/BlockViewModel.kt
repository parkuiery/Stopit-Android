package com.uiery.keep

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.service.DAILY_EMERGENCY_UNLOCK_LIMIT
import com.uiery.keep.service.EmergencyUnlockData
import com.uiery.keep.service.EmergencyUnlockState
import com.uiery.keep.service.emergencyUnlockDailyRemaining
import com.uiery.keep.service.isEmergencyUnlockDailyLimitReached
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.util.Calendar
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
            checkDailyLimit()
        }

        private fun checkDailyLimit() = intent {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val count = emergencyUnlockDao.countToday(calendar.timeInMillis)
            reduce {
                state.copy(
                    dailyLimitReached = isEmergencyUnlockDailyLimitReached(count),
                    dailyUnlockRemaining = emergencyUnlockDailyRemaining(count),
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
            val expireTime = now + durationMinutes * 60_000L
            val unlockCountRemaining = (state.dailyUnlockRemaining - 1).coerceAtLeast(0)

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

            checkDailyLimit()
            postSideEffect(BlockSideEffect.UnlockCompleted)
        }
    }

data class BlockUiState(
    val isShowEmergencyUnlockSheet: Boolean = false,
    val dailyLimitReached: Boolean = false,
    val dailyUnlockRemaining: Int = DAILY_EMERGENCY_UNLOCK_LIMIT,
)

sealed class BlockSideEffect {
    data object UnlockCompleted : BlockSideEffect()
}
