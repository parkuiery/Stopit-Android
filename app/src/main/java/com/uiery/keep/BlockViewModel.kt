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
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockRequestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BlockViewModel
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val analytics: KeepAnalytics,
        private val emergencyUnlockCoordinator: EmergencyUnlockCoordinator,
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
                routineId = routineId,
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

        private fun checkDailyLimit() = intent {
            val availability = emergencyUnlockCoordinator.readAvailability()
            reduce {
                state.copy(
                    emergencyUnlockEnabled = availability.enabled,
                    emergencyUnlockDailyLimit = availability.dailyLimit,
                    emergencyUnlockDurationOptions = availability.durationOptions,
                    emergencyUnlockReasonRequired = availability.reasonRequired,
                    dailyLimitReached = availability.dailyLimitReached,
                    dailyUnlockRemaining = availability.dailyUnlockRemaining,
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
            when (
                emergencyUnlockCoordinator.completeUnlock(
                    source = AnalyticsSource.BLOCK_SCREEN,
                    reason = reason,
                    customReason = customReason,
                    apps = apps,
                    durationMinutes = durationMinutes,
                )
            ) {
                is EmergencyUnlockRequestResult.Rejected -> {
                    checkDailyLimit()
                    return@intent
                }

                is EmergencyUnlockRequestResult.Completed -> {
                    checkDailyLimit()
                    postSideEffect(BlockSideEffect.UnlockCompleted)
                }
            }
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
