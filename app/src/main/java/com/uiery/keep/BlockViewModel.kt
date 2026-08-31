package com.uiery.keep

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockCancelSource
import com.uiery.keep.analytics.AnalyticsParentModeBlockContext
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.BlockAnalyticsCoordinator
import com.uiery.keep.analytics.BlockAnalyticsRequest
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.data.repeatblock.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.domain.repeatblock.AppCategoryResolver
import com.uiery.keep.domain.repeatblock.RepeatBlockHistorySample
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestionPolicy
import com.uiery.keep.data.lockhistory.LockHistoryRepository
import com.uiery.keep.lockscreen.LockScreenEntry
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_ENABLED
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_SECONDS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.EmergencyUnlockAvailabilityReason
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.domain.parentmode.ParentModeBlockReason
import com.uiery.keep.domain.pomodoro.PomodoroBlockContext
import com.uiery.keep.domain.pomodoro.PomodoroBlockContextSource
import com.uiery.keep.domain.parentmode.ParentModeBlockReasonSource
import com.uiery.keep.service.EmergencyUnlockRequestResult
import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.firstOrNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BlockViewModel
    @Inject
    constructor(
        private val blockingStateStore: BlockingStateStore,
        private val analytics: KeepAnalytics,
        private val blockAnalyticsCoordinator: BlockAnalyticsCoordinator,
        private val emergencyUnlockCoordinator: EmergencyUnlockCoordinator,
        private val lockHistoryRepository: LockHistoryRepository,
        private val routineRepository: RoutineRepository,
        private val repeatBlockSuggestionStore: RepeatBlockRoutineSuggestionStore,
        private val appCategoryResolver: AppCategoryResolver,
        private val parentModeBlockReasonSource: ParentModeBlockReasonSource,
        private val pomodoroBlockContextSource: PomodoroBlockContextSource,
    ) : ViewModel(),
        ContainerHost<BlockUiState, BlockSideEffect> {
        override val container: Container<BlockUiState, BlockSideEffect> = container(BlockUiState())

        init {
            analytics.logScreenView(KeepAnalyticsScreen.BLOCK)
            checkDailyLimit()
        }

        internal fun syncManualTimedLockReentry(
            entry: LockScreenEntry,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ) = syncManualTimedLockReentry(
            blockSource = entry.blockSource,
            now = now,
            zone = zone,
        )

        /**
         * Loads why parent mode put this screen up.
         *
         * The child holding the phone did not set the session up and has no other way to find out,
         * and the two reasons need different words — see [ParentModeBlockReason].
         */
        internal fun syncParentModeBlockReason(
            entry: LockScreenEntry,
            nowMillis: Long = System.currentTimeMillis(),
        ) = intent {
            if (entry.blockSource != AnalyticsBlockSource.PARENT_MODE) {
                reduce { state.copy(parentModeBlockReason = null) }
                return@intent
            }

            val reason = parentModeBlockReasonSource.blockReason(nowMillis)
            reduce { state.copy(parentModeBlockReason = reason) }
        }

        /**
         * 집중 세션이 이 화면을 띄웠다면 지금이 어느 구간인지 알려준다.
         *
         * 특히 휴식 중이면 "쉬는 중인데 왜 안 열리지"가 첫 반응이다. 그 순간이 휴식에도 차단이
         * 유지된다는 계약을 다시 말해야 하는 자리다.
         */
        internal fun syncPomodoroBlockContext(
            blockSource: String,
            now: Instant = Instant.now(),
        ) = intent {
            if (blockSource != AnalyticsBlockSource.POMODORO) {
                reduce { state.copy(pomodoroBlockContext = null) }
                return@intent
            }

            val context = pomodoroBlockContextSource.blockContext(now)
            reduce { state.copy(pomodoroBlockContext = context) }
        }

        internal fun syncManualTimedLockReentry(
            blockSource: String,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ) = intent {
            if (blockSource != AnalyticsBlockSource.TIMED_LOCK) {
                reduce { state.copy(timedLockDeadline = null) }
                return@intent
            }

            val storedDeadline = blockingStateStore.readLockTime()
            val deadline = ManualLockTimePolicy.toLocalDateTime(storedDeadline = storedDeadline, zone = zone)
            if (deadline == null || !ManualLockTimePolicy.isActiveAt(storedDeadline, now = now, zone = zone)) {
                reduce { state.copy(timedLockDeadline = null) }
                postSideEffect(BlockSideEffect.TimedLockExpired)
                return@intent
            }

            reduce { state.copy(timedLockDeadline = deadline) }
        }

        internal fun trackBlockShown(entry: LockScreenEntry) = trackBlockShown(
            packageName = entry.blockedPackageName,
            blockSource = entry.blockSource,
            routineId = entry.routineId,
            goalLockId = entry.goalLockId,
        )

        internal fun trackBlockShown(
            packageName: String,
            blockSource: String,
            routineId: String?,
            goalLockId: String? = null,
        ) = intent {
            val normalizedGoalLockId = goalLockId?.trim()?.takeIf { it.isNotEmpty() }
            val result = blockAnalyticsCoordinator.track(
                request = BlockAnalyticsRequest(
                    packageName = packageName,
                    blockSource = blockSource,
                    routineId = routineId,
                    goalLockId = normalizedGoalLockId,
                ),
                afterAppBlockTracked = {
                    if (blockSource == AnalyticsBlockSource.PARENT_MODE) {
                        analytics.trackParentModeBlockIntercepted(
                            blockContext = AnalyticsParentModeBlockContext.DISALLOWED_APP,
                        )
                    }
                },
            )
            reduce { state.copy(showFirstCoreActionFeedback = result.showFirstCoreActionFeedback) }
            val suggestion = if (blockSource.shouldShowPostBlockRepeatBlockSuggestion()) {
                loadPostBlockRepeatBlockRoutineSuggestion()
            } else {
                null
            }
            reduce { state.copy(repeatBlockRoutineSuggestion = suggestion) }
            if (suggestion != null) {
                analytics.trackRepeatBlockRoutineSuggestionShown(
                    surface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                    suggestion = suggestion.toAnalyticsPayload(),
                )
            }
        }

        internal fun dismissRepeatBlockRoutineSuggestion() = intent {
            val suggestion = state.repeatBlockRoutineSuggestion ?: return@intent
            repeatBlockSuggestionStore.recordDismissed(
                suggestion = suggestion,
                dismissedAt = LocalDateTime.now(),
            )
            analytics.trackRepeatBlockRoutineSuggestionDismissed(
                surface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                suggestion = suggestion.toAnalyticsPayload(),
            )
            reduce { state.copy(repeatBlockRoutineSuggestion = null) }
        }

        internal fun openRepeatBlockRoutineSuggestion() = intent {
            val suggestion = state.repeatBlockRoutineSuggestion ?: return@intent
            analytics.trackRepeatBlockRoutineSuggestionClicked(
                surface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                suggestion = suggestion.toAnalyticsPayload(),
            )
            postSideEffect(BlockSideEffect.NavigateRoutineWithRepeatBlockPrefill(suggestion))
        }

        private suspend fun loadPostBlockRepeatBlockRoutineSuggestion(): RepeatBlockRoutineSuggestion? {
            val now = LocalDateTime.now()
            val startMillis = now.minusDays(14)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val endMillis = now.plusDays(1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val histories = lockHistoryRepository.sessionsInRange(startMillis, endMillis)
                .firstOrNull()
                .orEmpty()
                .map { history ->
                    RepeatBlockHistorySample(
                        startDateTime = history.startDateTime,
                        blockedPackages = history.lockedApps,
                    )
                }
            return RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
                histories = histories,
                activeRoutines = routineRepository.fetchAll().firstOrNull().orEmpty(),
                dismissedSuggestions = repeatBlockSuggestionStore.readDismissedSuggestions(),
                categoryOf = appCategoryResolver::categoryOf,
                now = now,
            )
        }

        private fun checkDailyLimit() = intent {
            val availability = emergencyUnlockCoordinator.readAvailability()
            reduce {
                state.copy(
                    emergencyUnlockEnabled = availability.enabled,
                    emergencyUnlockDailyLimit = availability.dailyLimit,
                    emergencyUnlockDurationOptions = availability.durationOptions,
                    emergencyUnlockReasonRequired = availability.reasonRequired,
                    emergencyUnlockCountdownEnabled = availability.countdownEnabled,
                    emergencyUnlockCountdownSeconds = availability.countdownSeconds,
                    emergencyUnlockAvailabilityReason = availability.reason,
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

        internal fun trackEmergencyUnlockStepViewed(stepName: String) {
            analytics.trackEmergencyUnlockStepViewed(
                stepName = stepName,
                reasonRequiredEnabled = container.stateFlow.value.emergencyUnlockReasonRequired,
                source = AnalyticsSource.BLOCK_SCREEN,
            )
        }

        internal fun trackEmergencyUnlockValidationBlocked(
            stepName: String,
            validationReason: String,
        ) {
            analytics.trackEmergencyUnlockValidationBlocked(
                stepName = stepName,
                validationReason = validationReason,
                reasonRequiredEnabled = container.stateFlow.value.emergencyUnlockReasonRequired,
                source = AnalyticsSource.BLOCK_SCREEN,
            )
        }

        internal fun trackEmergencyUnlockCancelled(stepName: String) {
            analytics.trackEmergencyUnlockCancelled(
                stepName = stepName,
                reasonRequiredEnabled = container.stateFlow.value.emergencyUnlockReasonRequired,
                source = AnalyticsSource.BLOCK_SCREEN,
                cancelSource = AnalyticsEmergencyUnlockCancelSource.CANCEL_BUTTON,
            )
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
    val emergencyUnlockCountdownEnabled: Boolean = DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_ENABLED,
    val emergencyUnlockCountdownSeconds: Int = DEFAULT_EMERGENCY_UNLOCK_COUNTDOWN_SECONDS,
    val emergencyUnlockAvailabilityReason: EmergencyUnlockAvailabilityReason = EmergencyUnlockAvailabilityReason.Available,
    val showFirstCoreActionFeedback: Boolean = false,
    val timedLockDeadline: LocalDateTime? = null,
    val repeatBlockRoutineSuggestion: RepeatBlockRoutineSuggestion? = null,
    val parentModeBlockReason: ParentModeBlockReason? = null,
    val pomodoroBlockContext: PomodoroBlockContext? = null,
)

sealed class BlockSideEffect {
    data object UnlockCompleted : BlockSideEffect()
    data object TimedLockExpired : BlockSideEffect()
    data class NavigateRoutineWithRepeatBlockPrefill(
        val suggestion: RepeatBlockRoutineSuggestion,
    ) : BlockSideEffect()
}

internal fun String?.orDefaultBlockSource(): String =
    when (this) {
        AnalyticsBlockSource.MANUAL_KEEP,
        AnalyticsBlockSource.TIMED_LOCK,
        AnalyticsBlockSource.ROUTINE,
        AnalyticsBlockSource.GOAL_LOCK,
        AnalyticsBlockSource.POMODORO,
        AnalyticsBlockSource.PARENT_MODE -> this
        else -> AnalyticsBlockSource.MANUAL_KEEP
    }

private fun String.shouldShowPostBlockRepeatBlockSuggestion(): Boolean = this != AnalyticsBlockSource.GOAL_LOCK

private fun RepeatBlockRoutineSuggestion.toAnalyticsPayload() = RepeatBlockRoutineSuggestionAnalyticsPayload(
    reason = reason.analyticsValue,
    timeBucket = timeBucket.analyticsValue,
    dayType = dayType.analyticsValue,
    categoryBucket = categoryBucket.analyticsValue,
    repeatCountBucket = repeatCountBucket.analyticsValue,
    routineCoverageState = routineCoverageState.analyticsValue,
)
