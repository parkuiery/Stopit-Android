package com.uiery.keep.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.uiery.keep.BlockActivity
import com.uiery.keep.BuildConfig
import com.uiery.keep.R
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.data.parentmode.ParentModeSessionStore
import com.uiery.keep.datastore.AccessibilityBlockingSnapshot
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.dataStore
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toDayOfWeekList
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class KeepAccessibilityService :
    AccessibilityService(),
    CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RoutineRuntimeEntryPoint {
        fun routineRepository(): RoutineRepository

        fun goalLockRepository(): GoalLockRepository

        fun blockingStateStore(): BlockingStateStore

        fun emergencyUnlockNotificationHelper(): EmergencyUnlockNotificationHelper
    }

    @Volatile
    private var cachedPrefs = AccessibilityBlockingSnapshot()

    @Volatile
    private var cachedRoutines: List<RoutineModel> = emptyList()

    @Volatile
    private var cachedGoalLocks: List<GoalLock> = emptyList()

    @Volatile
    private var cachedParentModeSession: ParentModeSession? = null

    private val handler = Handler(Looper.getMainLooper())
    private val isCleaningUp = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastBlockKey: String? = null
    private var lastBlockElapsedRealtime: Long = 0L
    private var scheduledEmergencyUnlockExpireTime: Long = 0L
    private var emergencyUnlockExpiryRunnable: Runnable? = null
    private var scheduledEmergencyUnlockCountdownExpireTime: Long = 0L
    private var emergencyUnlockCountdownRunnable: Runnable? = null
    private var timeBasedStartReevaluationRunnable: Runnable? = null

    companion object {
        private const val SAME_BLOCK_DEDUPE_WINDOW_MS = 1_500L
        private val FOREGROUND_REEVALUATION_RETRY_DELAYS_MS = longArrayOf(300L, 900L, 1_500L)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        KeepAccessibilityServiceDebugState.update(applicationContext) { it.copy(isServiceConnected = true) }
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RoutineRuntimeEntryPoint::class.java,
        )
        launch {
            entryPoint.blockingStateStore().accessibilitySnapshot
                .withAccessibilityRuntimeRecovery(
                    source = AccessibilityRuntimeFlowSource.BlockingState,
                    onRecoveryEvent = ::recordRuntimeFlowRecovery,
                )
                .collect { snapshot ->
                    cachedPrefs = snapshot
                    KeepAccessibilityServiceDebugState.update(applicationContext) {
                        it.copy(
                            observedIsKeep = cachedPrefs.isKeep,
                            observedPreventUninstall = cachedPrefs.preventUninstall,
                            observedSelectedAppPackages = cachedPrefs.selectedAppPackages,
                            observedEmergencyUnlockApps = cachedPrefs.emergencyUnlockApps,
                            observedEmergencyUnlockExpireTimeMillis = cachedPrefs.emergencyUnlockExpireTimeMillis,
                        )
                    }
                    scheduleEmergencyUnlockExpiryCheck(cachedPrefs.emergencyUnlockExpireTimeMillis)
                    syncEmergencyUnlockCountdownNotification(
                        expireTimeMillis = cachedPrefs.emergencyUnlockExpireTimeMillis,
                        notificationHelper = entryPoint.emergencyUnlockNotificationHelper(),
                    )
                    reevaluateCurrentForegroundAfterStateUpdate()
                }
        }
        launch {
            entryPoint.routineRepository().fetchAll()
                .withAccessibilityRuntimeRecovery(
                    source = AccessibilityRuntimeFlowSource.Routines,
                    onRecoveryEvent = ::recordRuntimeFlowRecovery,
                )
                .collect { routines ->
                    cachedRoutines = routines
                    reevaluateCurrentForegroundAfterStateUpdate()
                    scheduleNextTimeBasedStartReevaluation()
                }
        }
        launch {
            entryPoint.goalLockRepository().fetchAll()
                .withAccessibilityRuntimeRecovery(
                    source = AccessibilityRuntimeFlowSource.GoalLocks,
                    onRecoveryEvent = ::recordRuntimeFlowRecovery,
                )
                .collect { goalLocks ->
                    cachedGoalLocks = goalLocks
                    reevaluateCurrentForegroundAfterStateUpdate()
                    scheduleNextTimeBasedStartReevaluation()
                }
        }
        launch {
            ParentModeSessionStore(applicationContext.dataStore).observe()
                .withAccessibilityRuntimeRecovery(
                    source = AccessibilityRuntimeFlowSource.ParentMode,
                    onRecoveryEvent = ::recordRuntimeFlowRecovery,
                )
                .collect { session ->
                    cachedParentModeSession = session
                    updateParentModeDebugState(session)
                    reevaluateCurrentForegroundAfterStateUpdate()
                    scheduleNextTimeBasedStartReevaluation()
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val prefs = cachedPrefs

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        KeepAccessibilityServiceDebugState.update(applicationContext) {
            it.copy(lastWindowStateChangedPackage = packageName)
        }

        cleanupExpiredEmergencyUnlock()
        if (isEmergencyUnlocked(packageName)) return

        if (prefs.preventUninstall && isUninstallAttempt(event = event, eventPackageName = packageName)) {
            dismissUninstallScreen()
            return
        }

        blockIfNeeded(packageName = packageName, prefs = prefs)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        KeepAccessibilityServiceDebugState.reset(applicationContext)
        job.cancel()
    }

    private fun recordRuntimeFlowRecovery(event: AccessibilityRuntimeFlowRecoveryEvent) {
        KeepAccessibilityServiceDebugState.update(applicationContext) {
            it.copy(
                lastRuntimeFlowErrorSource = event.source.debugName,
                lastRuntimeFlowErrorType = event.errorType,
                lastRuntimeFlowRetryAttempt = event.attempt,
                lastRuntimeFlowRetryDelayMillis = event.retryDelayMillis,
            )
        }
    }

    private fun updateParentModeDebugState(session: ParentModeSession?) {
        KeepAccessibilityServiceDebugState.update(applicationContext) {
            it.copy(
                observedParentModeState = session?.toDebugStateValue(),
                observedParentModeAllowedAppCount = session?.allowedApps?.size ?: 0,
            )
        }
    }

    private fun ParentModeSession.toDebugStateValue(nowMillis: Long = System.currentTimeMillis()): String {
        val stateName = state.name
        return when {
            stateName == "Active" && expiresAtMillis <= nowMillis -> "expired"
            stateName == "Setup" -> "setup"
            stateName == "Active" -> "active"
            stateName == "Expired" -> "expired"
            stateName == "UnlockedByPin" -> "unlocked_by_pin"
            stateName == "Cancelled" -> "cancelled"
            else -> stateName
        }
    }

    private fun blockIfNeeded(
        packageName: String,
        prefs: AccessibilityBlockingSnapshot,
    ) {
        val blockRequest = resolveForegroundBlockRequest(
            packageName = packageName,
            prefs = AccessibilityBlockingPreferences(
                isKeep = prefs.isKeep,
                lockTime = prefs.lockTime,
                selectedAppPackages = prefs.selectedAppPackages,
            ),
            cachedRoutines = cachedRoutines,
            cachedGoalLocks = cachedGoalLocks,
            parentModeSession = cachedParentModeSession,
            parentControlPackages = setOf(BuildConfig.APPLICATION_ID),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        ) ?: return

        if (isDuplicateBlock(packageName = packageName, blockSource = blockRequest.blockSource)) return

        launchBlockActivity(blockRequest)
    }

    private fun reevaluateCurrentForegroundAfterStateUpdate() {
        handler.post {
            reevaluateCurrentForegroundOnce()
            FOREGROUND_REEVALUATION_RETRY_DELAYS_MS.forEach { delayMillis ->
                handler.postDelayed({ reevaluateCurrentForegroundOnce() }, delayMillis)
            }
        }
    }

    private fun scheduleNextTimeBasedStartReevaluation() {
        handler.post {
            timeBasedStartReevaluationRunnable?.let(handler::removeCallbacks)
            timeBasedStartReevaluationRunnable = null
            val delayMillis = nextTimeBasedBlockingStartReevaluationDelayMillis(
                routines = cachedRoutines,
                goalLocks = cachedGoalLocks,
                parentModeSession = cachedParentModeSession,
            ) ?: return@post
            val runnable = Runnable {
                updateParentModeDebugState(cachedParentModeSession)
                reevaluateCurrentForegroundAfterStateUpdate()
                scheduleNextTimeBasedStartReevaluation()
            }
            timeBasedStartReevaluationRunnable = runnable
            handler.postDelayed(runnable, delayMillis)
        }
    }

    private fun reevaluateCurrentForegroundOnce() {
        val packageName = currentForegroundPackage() ?: return
        val prefs = cachedPrefs

        cleanupExpiredEmergencyUnlock()
        if (isEmergencyUnlocked(packageName)) return

        if (prefs.preventUninstall && isUninstallAttempt(eventPackageName = packageName)) {
            dismissUninstallScreen()
            return
        }

        val blockRequest = resolveServiceConnectionForegroundBlockRequest(
            currentForegroundPackage = packageName,
            prefs = AccessibilityBlockingPreferences(
                isKeep = prefs.isKeep,
                lockTime = prefs.lockTime,
                selectedAppPackages = prefs.selectedAppPackages,
            ),
            cachedRoutines = cachedRoutines,
            cachedGoalLocks = cachedGoalLocks,
            parentModeSession = cachedParentModeSession,
            parentControlPackages = setOf(BuildConfig.APPLICATION_ID),
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        ) ?: return

        if (isDuplicateBlock(packageName = blockRequest.packageName, blockSource = blockRequest.blockSource)) return

        launchBlockActivity(blockRequest)
    }

    private fun launchBlockActivity(blockRequest: ForegroundBlockRequest) {
        KeepAccessibilityServiceDebugState.update(applicationContext) {
            it.copy(
                lastLaunchedBlockPackage = blockRequest.packageName,
                lastLaunchedBlockSource = blockRequest.blockSource,
                lastLaunchedRoutineId = blockRequest.routineId,
                lastLaunchedGoalLockId = blockRequest.goalLockId,
            )
        }
        val intent = Intent(this, BlockActivity::class.java)
        intent.putExtra(BlockActivity.EXTRA_PACKAGE_NAME, blockRequest.packageName)
        intent.putExtra(BlockActivity.EXTRA_BLOCK_SOURCE, blockRequest.blockSource)
        blockRequest.routineId?.let { intent.putExtra(BlockActivity.EXTRA_ROUTINE_ID, it) }
        blockRequest.goalLockId?.let { intent.putExtra(BlockActivity.EXTRA_GOAL_LOCK_ID, it) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun isUninstallAttempt(
        event: AccessibilityEvent? = null,
        eventPackageName: String,
    ): Boolean {
        if (eventPackageName !in KNOWN_UNINSTALL_PACKAGES) return false
        val appName = getString(R.string.app_name)
        val hasEventTextMatch = event?.text.orEmpty().any { text ->
            val value = text?.toString().orEmpty()
            value.contains(BuildConfig.APPLICATION_ID, ignoreCase = true) ||
                value.contains(appName, ignoreCase = true)
        }
        val rootNode = rootInActiveWindow ?: return shouldInterceptUninstallAttempt(
            eventPackageName = eventPackageName,
            hasApplicationIdMatch = false,
            hasAppNameMatch = false,
            hasEventTextMatch = hasEventTextMatch,
        )
        try {
            val idNodes = rootNode.findAccessibilityNodeInfosByText(BuildConfig.APPLICATION_ID)
            val hasApplicationIdMatch = !idNodes.isNullOrEmpty()
            idNodes?.forEach { it.recycle() }
            if (hasApplicationIdMatch) return true

            val nameNodes = rootNode.findAccessibilityNodeInfosByText(appName)
            val hasAppNameMatch = !nameNodes.isNullOrEmpty()
            nameNodes?.forEach { it.recycle() }
            return shouldInterceptUninstallAttempt(
                eventPackageName = eventPackageName,
                hasApplicationIdMatch = hasApplicationIdMatch,
                hasAppNameMatch = hasAppNameMatch,
                hasEventTextMatch = hasEventTextMatch,
            )
        } finally {
            rootNode.recycle()
        }
    }

    private fun dismissUninstallScreen() {
        KeepAccessibilityServiceDebugState.update(applicationContext) {
            it.copy(lastDismissedUninstallPackage = BuildConfig.APPLICATION_ID)
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        launchHomeScreen()

        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            rootInActiveWindow?.recycle()
            launchHomeScreen()
        }, 500)
    }

    private fun launchHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    private fun isEmergencyUnlocked(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val snapshot = EmergencyUnlockState.current
        if (isEmergencyUnlockActiveForPackage(
                packageName = packageName,
                unlockedApps = snapshot.unlockedApps,
                expireTimeMillis = snapshot.expireTimeMillis,
                nowMillis = now,
            )) {
            return true
        }
        val prefs = cachedPrefs
        if (isEmergencyUnlockActiveForPackage(
                packageName = packageName,
                unlockedApps = prefs.emergencyUnlockApps,
                expireTimeMillis = prefs.emergencyUnlockExpireTimeMillis,
                nowMillis = now,
            )) {
            return true
        }
        return false
    }

    private fun cleanupExpiredEmergencyUnlock() {
        val prefs = cachedPrefs
        if (prefs.emergencyUnlockExpireTimeMillis > 0 &&
            System.currentTimeMillis() >= prefs.emergencyUnlockExpireTimeMillis &&
            isCleaningUp.compareAndSet(false, true)) {
            EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
            launch {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    RoutineRuntimeEntryPoint::class.java,
                )
                entryPoint.blockingStateStore().clearEmergencyUnlockRuntimeState()
                isCleaningUp.set(false)
            }
        }
    }

    private fun scheduleEmergencyUnlockExpiryCheck(expireTimeMillis: Long) {
        handler.post {
            if (scheduledEmergencyUnlockExpireTime == expireTimeMillis) return@post

            cancelEmergencyUnlockExpiryCheck()
            val delayMillis = emergencyUnlockExpiryDelayMillis(expireTimeMillis) ?: return@post
            val expectedExpireTime = expireTimeMillis
            val expiryRunnable = Runnable {
                handleEmergencyUnlockExpired(expectedExpireTime)
            }

            scheduledEmergencyUnlockExpireTime = expectedExpireTime
            emergencyUnlockExpiryRunnable = expiryRunnable
            handler.postDelayed(expiryRunnable, delayMillis)
        }
    }

    private fun cancelEmergencyUnlockExpiryCheck() {
        emergencyUnlockExpiryRunnable?.let(handler::removeCallbacks)
        emergencyUnlockExpiryRunnable = null
        scheduledEmergencyUnlockExpireTime = 0L
    }

    private fun syncEmergencyUnlockCountdownNotification(
        expireTimeMillis: Long,
        notificationHelper: EmergencyUnlockNotificationHelper,
    ) {
        handler.post {
            if (expireTimeMillis <= 0L) {
                cancelEmergencyUnlockCountdownNotification(notificationHelper)
                return@post
            }
            if (scheduledEmergencyUnlockCountdownExpireTime == expireTimeMillis) return@post

            cancelEmergencyUnlockCountdownNotification(notificationHelper)
            scheduledEmergencyUnlockCountdownExpireTime = expireTimeMillis
            val countdownRunnable = object : Runnable {
                override fun run() {
                    val currentExpireTime = scheduledEmergencyUnlockCountdownExpireTime
                    if (currentExpireTime != expireTimeMillis) return

                    val delayMillis = emergencyUnlockNotificationTickDelayMillis(expireTimeMillis)
                    if (delayMillis == null) {
                        cancelEmergencyUnlockCountdownNotification(notificationHelper)
                        return
                    }

                    val postResult = notificationHelper.syncWithStoredExpireTime(expireTimeMillis)
                    KeepAccessibilityServiceDebugState.update(applicationContext) {
                        it.copy(
                            lastCountdownNotificationExpireTimeMillis = expireTimeMillis,
                            lastCountdownNotificationPostResult = postResult?.name,
                        )
                    }
                    handler.postDelayed(this, delayMillis)
                }
            }
            emergencyUnlockCountdownRunnable = countdownRunnable
            countdownRunnable.run()
        }
    }

    private fun cancelEmergencyUnlockCountdownNotification(
        notificationHelper: EmergencyUnlockNotificationHelper,
    ) {
        emergencyUnlockCountdownRunnable?.let(handler::removeCallbacks)
        emergencyUnlockCountdownRunnable = null
        scheduledEmergencyUnlockCountdownExpireTime = 0L
        notificationHelper.cancel()
    }

    private fun handleEmergencyUnlockExpired(expectedExpireTimeMillis: Long) {
        emergencyUnlockExpiryRunnable = null
        if (scheduledEmergencyUnlockExpireTime == expectedExpireTimeMillis) {
            scheduledEmergencyUnlockExpireTime = 0L
        }

        val prefs = cachedPrefs
        val foregroundPackage = currentForegroundPackage()
        val isForegroundStillEmergencyUnlocked = foregroundPackage?.let(::isEmergencyUnlocked) ?: false

        launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                RoutineRuntimeEntryPoint::class.java,
            )
            val resolution = handleExpiredEmergencyUnlockForContext(
                context = this@KeepAccessibilityService,
                expectedExpireTimeMillis = expectedExpireTimeMillis,
                currentExpireTimeMillis = prefs.emergencyUnlockExpireTimeMillis,
                expiredUnlockedApps = prefs.emergencyUnlockApps,
                foregroundPackage = foregroundPackage,
                applicationId = BuildConfig.APPLICATION_ID,
                isForegroundStillEmergencyUnlocked = isForegroundStillEmergencyUnlocked,
                clearExpiredEmergencyUnlockState = entryPoint.blockingStateStore()::clearEmergencyUnlockRuntimeState,
            )

            resolution.packageToReblock?.let { packageName ->
                blockIfNeeded(packageName = packageName, prefs = cachedPrefs)
            }
        }
    }

    private fun currentForegroundPackage(): String? {
        val root = rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            root.recycle()
        }
    }
    private fun isDuplicateBlock(
        packageName: String,
        blockSource: String,
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        val blockKey = "$packageName:$blockSource"
        val isDuplicate =
            blockKey == lastBlockKey &&
                now - lastBlockElapsedRealtime < SAME_BLOCK_DEDUPE_WINDOW_MS
        if (!isDuplicate) {
            lastBlockKey = blockKey
            lastBlockElapsedRealtime = now
        }
        return isDuplicate
    }
}

internal fun nextTimeBasedBlockingStartReevaluationDelayMillis(
    routines: List<RoutineModel>,
    goalLocks: List<GoalLock>,
    parentModeSession: ParentModeSession? = null,
    now: LocalDateTime = LocalDateTime.now(),
    nowMillis: Long = System.currentTimeMillis(),
): Long? = listOfNotNull(
    nextRoutineStartReevaluationDelayMillis(routines = routines, now = now),
    nextGoalLockStartReevaluationDelayMillis(goalLocks = goalLocks, now = now),
    nextParentModeExpirationReevaluationDelayMillis(
        parentModeSession = parentModeSession,
        nowMillis = nowMillis,
    ),
).minOrNull()

internal fun nextParentModeExpirationReevaluationDelayMillis(
    parentModeSession: ParentModeSession?,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val session = parentModeSession ?: return null
    if (session.state.name != "Active") return null
    val delayMillis = session.expiresAtMillis - nowMillis
    return delayMillis.takeIf { it > 0L }
}

internal fun nextGoalLockStartReevaluationDelayMillis(
    goalLocks: List<GoalLock>,
    now: LocalDateTime = LocalDateTime.now(),
): Long? = goalLocks
    .asSequence()
    .filter { goalLock ->
        goalLock.status == GoalLockStoredStatus.Active &&
            goalLock.selectedPackages.isNotEmpty() &&
            !goalLock.startDate.isAfter(goalLock.endDate) &&
            GoalLockPolicy.isValidForCreation(goalLock)
    }
    .flatMap { goalLock -> nextGoalLockStartCandidates(goalLock = goalLock, now = now).asSequence() }
    .filter { candidateStart -> candidateStart.isAfter(now) }
    .map { candidateStart -> Duration.between(now, candidateStart).toMillis() }
    .minOrNull()

private fun nextGoalLockStartCandidates(
    goalLock: GoalLock,
    now: LocalDateTime,
): List<LocalDateTime> = when (val mode = goalLock.lockMode) {
    GoalLockMode.AllDay -> listOf(goalLock.startDate.atStartOfDay())
    is GoalLockMode.Scheduled -> {
        val firstDate = maxOf(goalLock.startDate, now.toLocalDate())
        if (firstDate.isAfter(goalLock.endDate)) {
            emptyList()
        } else {
            val lastDate = minOf(goalLock.endDate, firstDate.plusDays(7))
            generateSequence(firstDate) { date -> date.plusDays(1).takeIf { !it.isAfter(lastDate) } }
                .filter { date -> date.dayOfWeek in mode.repeatDays }
                .map { date -> date.atTime(mode.startTime) }
                .toList()
        }
    }
}

internal fun nextRoutineStartReevaluationDelayMillis(
    routines: List<RoutineModel>,
    now: LocalDateTime = LocalDateTime.now(),
): Long? = routines
    .asSequence()
    .filter { routine -> routine.isEnabled && !routine.lockApplications.isNullOrEmpty() }
    .flatMap { routine ->
        routine.repeatDays.toDayOfWeekList().asSequence().mapNotNull { dayOfWeek ->
            val startDate = nextDateForDayOfWeek(dayOfWeek = dayOfWeek, now = now)
            val candidateStart = startDate.atTime(
                routine.startTime.hour,
                routine.startTime.minute,
                routine.startTime.second,
            )
            if (candidateStart.isAfter(now)) {
                Duration.between(now, candidateStart).toMillis()
            } else {
                Duration.between(now, candidateStart.plusWeeks(1)).toMillis()
            }
        }
    }
    .minOrNull()

private fun nextDateForDayOfWeek(
    dayOfWeek: java.time.DayOfWeek,
    now: LocalDateTime,
): java.time.LocalDate {
    val daysUntil = Math.floorMod(dayOfWeek.value - now.dayOfWeek.value, 7)
    return now.toLocalDate().plusDays(daysUntil.toLong())
}

internal suspend fun handleExpiredEmergencyUnlockForContext(
    context: Context,
    expectedExpireTimeMillis: Long,
    currentExpireTimeMillis: Long,
    expiredUnlockedApps: Set<String>,
    foregroundPackage: String?,
    applicationId: String,
    isForegroundStillEmergencyUnlocked: Boolean,
    clearExpiredEmergencyUnlockState: suspend () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
): EmergencyUnlockExpiryResolution {
    val resolution = resolveEmergencyUnlockExpiry(
        expectedExpireTimeMillis = expectedExpireTimeMillis,
        currentExpireTimeMillis = currentExpireTimeMillis,
        expiredUnlockedApps = expiredUnlockedApps,
        foregroundPackage = foregroundPackage,
        applicationId = applicationId,
        isForegroundStillEmergencyUnlocked = isForegroundStillEmergencyUnlocked,
        nowMillis = nowMillis,
    )

    if (resolution.shouldClearState) {
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
        clearExpiredEmergencyUnlockState()
        cancelEmergencyUnlockNotification(context)
    }

    return resolution
}
