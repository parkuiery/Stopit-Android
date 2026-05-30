package com.uiery.keep.feature.home

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.ReviewEligibilityDecision
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.SkipReason
import com.uiery.keep.service.recordLockHistorySession
import com.uiery.keep.util.timeNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val analytics: KeepAnalytics,
        private val lockHistoryDao: LockHistoryDao,
        private val reviewEligibility: ReviewEligibilityEvaluator,
        private val inAppReviewManager: InAppReviewManager,
    ) : ViewModel(),
        ContainerHost<HomeUiState, HomeSideEffect> {
        override val container: Container<HomeUiState, HomeSideEffect> = container(HomeUiState())

        init {
            getIsKeep()
            getSelectedApp()
        }

        internal fun changeIsKeep() =
            intent {
                val isKeep = !state.isKeep
                analytics.trackKeepModeToggled(isEnabled = isKeep)
                if (isKeep) {
                    trackFirstLockConfiguredIfNeeded(source = AnalyticsSource.HOME)
                    analytics.trackLockSessionStart(
                        source = AnalyticsSource.HOME_KEEP_SWITCH,
                        isRoutine = false,
                    )
                    storeStartTime()
                } else {
                    analytics.trackLockSessionEnd(
                        source = AnalyticsSource.HOME_KEEP_SWITCH,
                        endReason = AnalyticsEndReason.USER_TOGGLE_OFF,
                        isRoutine = false,
                    )
                    storeBlockTime(System.currentTimeMillis() - state.startTime)
                }
                reduce { state.copy(isKeep = isKeep, startTime = System.currentTimeMillis()) }
                storeIsKeep()
            }

        internal fun showSnackBar(message: String) =
            intent {
                postSideEffect(HomeSideEffect.ShowSnackBar(message))
                CoroutineScope(Dispatchers.IO).launch {
                    reduce { state.copy(snackbarMessage = message) }
                }
            }

        internal fun showCategoryBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowCategoryBottomSheet = true,
                        sheetVisible = true,
                    )
                }
            }

        internal fun showTimeBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowTimeBottomSheet = true,
                        sheetVisible = true,
                    )
                }
            }

        internal fun hideCategoryBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowCategoryBottomSheet = false,
                        sheetVisible = state.isShowTimeBottomSheet,
                    )
                }
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (!pendingMessage.isNullOrBlank()) {
                    postSideEffect(HomeSideEffect.ShowSnackBar(pendingMessage))
                    reduce { state.copy(snackbarMessage = pendingMessage) }
                }
            }

        internal fun hideTimeBottomSheet() =
            intent {
                reduce {
                    state.copy(
                        isShowTimeBottomSheet = false,
                        sheetVisible = state.isShowCategoryBottomSheet,
                    )
                }
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (!pendingMessage.isNullOrBlank()) {
                    postSideEffect(HomeSideEffect.ShowSnackBar(pendingMessage))
                    reduce { state.copy(snackbarMessage = pendingMessage) }
                }
            }

        internal fun maybeDrainReviewFlag(activity: Activity?) =
            intent {
                val prefs = dataStore.data.firstOrNull()
                val pending = prefs?.get(PreferencesKey.REVIEW_PENDING) ?: false
                if (!pending) return@intent
                if (state.sheetVisible) {
                    analytics.reviewPromptSkipped(SkipReason.NotHomeRoot.name)
                    dataStore.edit { it[PreferencesKey.REVIEW_PENDING] = false }
                    return@intent
                }
                val live = reviewEligibility.evaluateLive()
                if (live is ReviewEligibilityDecision.Ineligible) {
                    analytics.reviewPromptSkipped(live.reason.name)
                    dataStore.edit { it[PreferencesKey.REVIEW_PENDING] = false }
                    return@intent
                }
                if (activity == null) {
                    analytics.reviewPromptSkipped(SkipReason.NoActivity.name)
                    return@intent
                }
                dataStore.edit { it[PreferencesKey.REVIEW_PENDING] = false }
                inAppReviewManager.launchIfReady(activity)
            }

        internal fun maybeDrainRoutineStartNotice() =
            intent {
                val pendingMessage = takePendingRoutineStartNoticeIfReady(sheetVisible = state.sheetVisible)
                if (pendingMessage.isNullOrBlank()) return@intent

                postSideEffect(HomeSideEffect.ShowSnackBar(pendingMessage))
                reduce { state.copy(snackbarMessage = pendingMessage) }
            }

        private suspend fun takePendingRoutineStartNoticeIfReady(sheetVisible: Boolean): String? {
            val pendingMessage = dataStore.data.firstOrNull()?.get(PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE)
            if (pendingMessage.isNullOrBlank()) return null
            if (sheetVisible) return null

            dataStore.edit { it.remove(PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE) }
            return pendingMessage
        }

        internal fun moveToLock() =
            intent {
                val targetDateTime = if (state.countdownDays > 0) {
                    calculateCountdownTargetDateTime(state.countdownDays, state.countdownTime)
                } else {
                    calculateTargetLockDateTime(state.blockTime)
                }
                postSideEffect(HomeSideEffect.MoveToLock(targetDateTime.toString(), false))
            }

        private fun getSelectedApp() =
            intent {
                val selectedAppPackage =
                    dataStore.data
                        .map { data ->
                            data[PreferencesKey.SELECTED_APP_PACKAGES].orEmpty()
                        }.firstOrNull()
                selectedAppPackage?.let {
                    reduce { state.copy(selectedAppPackage = it) }
                }
            }

        private fun storeSelectedApp(selectedAppPackage: Set<String>) =
            intent {
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.SELECTED_APP_PACKAGES] = selectedAppPackage
                }
            }

        private fun storeBlockTime(
            lockedMillis: Long,
            isRoutine: Boolean = false,
        ) = intent {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - lockedMillis
            recordLockHistorySession(
                dataStore = dataStore,
                lockHistoryDao = lockHistoryDao,
                startTimestamp = startTime,
                endTimestamp = endTime,
                lockedApps = state.selectedAppPackage,
                isRoutine = isRoutine,
            )
        }

        internal fun selectCategoryComplete(selectedAppPackage: Set<String>) =
            intent {
                analytics.trackAppSelectionCompleted(
                    selectedAppCount = selectedAppPackage.size,
                    isOnboarding = false,
                )
                storeSelectedApp(selectedAppPackage)
                reduce { state.copy(selectedAppPackage = selectedAppPackage) }
            }

        private fun storeIsKeep() =
            intent {
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.IS_KEEP] = state.isKeep
                }
            }

        private fun storeStartTime() =
            intent {
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.START_TIME] = System.currentTimeMillis()
                }
            }

        private fun getStartTime() =
            intent {
                val startTime =
                    dataStore.data
                        .map { preferences ->
                            preferences[PreferencesKey.START_TIME]
                        }.firstOrNull()
                reduce { state.copy(startTime = startTime ?: System.currentTimeMillis()) }
            }

        private fun getIsKeep() =
            intent {
                val isKeep =
                    dataStore.data
                        .map { preferences ->
                            preferences[PreferencesKey.IS_KEEP]
                        }.firstOrNull()
                reduce { state.copy(isKeep = isKeep == true) }
                if (isKeep == true) {
                    getStartTime()
                }
            }

        internal fun updateCountdownDuration(duration: CountdownDuration) =
            intent {
                val blockTime =
                    timeNow
                        .toJavaLocalTime()
                        .plusHours(duration.hour.toLong())
                        .plusMinutes(duration.minute.toLong())
                        .toKotlinLocalTime()
                reduce {
                    state.copy(
                        countdownTime = LocalTime(duration.hour, duration.minute),
                        countdownDays = duration.day,
                        blockTime = blockTime,
                    )
                }
            }

        internal fun updateTimerTime(timerTime: LocalTime) =
            intent {
                reduce { state.copy(timerTime = timerTime, blockTime = timerTime, countdownDays = 0) }
            }

        internal fun lockTime() =
            intent {
                val targetLockDateTime = if (state.countdownDays > 0) {
                    calculateCountdownTargetDateTime(state.countdownDays, state.countdownTime)
                } else {
                    calculateTargetLockDateTime(state.blockTime)
                }
                dataStore.edit { preferences ->
                    preferences[PreferencesKey.LOCK_TIME] = targetLockDateTime.toString()
                }
                val lockedDuration =
                    Duration
                        .between(LocalDateTime.now(), targetLockDateTime)
                        .toMillis()
                        .coerceAtLeast(0L)
                trackFirstLockConfiguredIfNeeded(source = AnalyticsSource.HOME_TIMER)
                analytics.trackLockScheduled(
                    scheduleType = if (state.countdownDays > 0) {
                        AnalyticsScheduleType.COUNTDOWN
                    } else {
                        AnalyticsScheduleType.TIMER
                    },
                    scheduledDurationMinutes = lockedDuration / 60_000L,
                )
                analytics.trackLockSessionStart(
                    source = AnalyticsSource.HOME_TIMER,
                    isRoutine = false,
                )
                storeBlockTime(lockedDuration)
            }

        private suspend fun trackFirstLockConfiguredIfNeeded(source: String) {
            val preferences = dataStore.data.firstOrNull()
            val hasTracked = preferences?.get(PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED) == true

            if (hasTracked) return

            val selectedAppCount =
                preferences
                    ?.get(PreferencesKey.SELECTED_APP_PACKAGES)
                    ?.size ?: 0

            analytics.trackFirstLockConfigured(
                source = source,
                selectedAppCount = selectedAppCount,
            )

            dataStore.edit { mutablePreferences ->
                mutablePreferences[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] = true
            }
        }

        private fun calculateTargetLockDateTime(blockTime: LocalTime): LocalDateTime {
            val nowDateTime = LocalDateTime.now()
            val target =
                nowDateTime
                    .withHour(blockTime.hour)
                    .withMinute(blockTime.minute)
                    .withSecond(0)
                    .withNano(0)

            return if (target.isBefore(nowDateTime)) target.plusDays(1) else target
        }

        private fun calculateCountdownTargetDateTime(days: Int, countdownTime: LocalTime): LocalDateTime =
            LocalDateTime.now()
                .plusDays(days.toLong())
                .plusHours(countdownTime.hour.toLong())
                .plusMinutes(countdownTime.minute.toLong())

        internal fun analyticsHomeScreen() =
            intent {
                analytics.logScreenView(KeepAnalyticsScreen.HOME)
            }
    }

data class HomeUiState(
    val isKeep: Boolean = false,
    val snackbarMessage: String = "",
    val isShowCategoryBottomSheet: Boolean = false,
    val isShowTimeBottomSheet: Boolean = false,
    val selectedAppPackage: Set<String> = emptySet(),
    val startTime: Long = System.currentTimeMillis(),
    val searchContent: String = "",
    val isSelectAll: Boolean = true,
    val blockTime: LocalTime = timeNow,
    val countdownTime: LocalTime = timeNow,
    val timerTime: LocalTime = timeNow,
    val countdownDays: Int = 0,
    val sheetVisible: Boolean = false,
)

data class CountdownDuration(val day: Int = 0, val hour: Int = 0, val minute: Int = 0)

sealed class HomeSideEffect {
    data class ShowSnackBar(
        val message: String,
    ) : HomeSideEffect()

    data class MoveToLock(
        val lockTime: String?,
        val isRoutine: Boolean,
    ) : HomeSideEffect()
}
