package com.uiery.keep.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.BlockActivity
import com.uiery.keep.BuildConfig
import com.uiery.keep.R
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class KeepAccessibilityService :
    AccessibilityService(),
    CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private data class CachedPreferences(
        val isKeep: Boolean = false,
        val lockTime: String? = null,
        val selectedAppPackages: Set<String> = emptySet(),
        val preventUninstall: Boolean = true,
        val emergencyUnlockApps: Set<String> = emptySet(),
        val emergencyUnlockExpireTime: Long = 0L,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RoutineRuntimeEntryPoint {
        fun routineDao(): RoutineDao

        @com.uiery.keep.KeepDataSource
        fun dataStore(): DataStore<Preferences>
    }

    @Volatile
    private var cachedPrefs = CachedPreferences()

    @Volatile
    private var cachedRoutines: List<RoutineModel> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private val isCleaningUp = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastBlockKey: String? = null
    private var lastBlockElapsedRealtime: Long = 0L
    private var scheduledEmergencyUnlockExpireTime: Long = 0L
    private var emergencyUnlockExpiryRunnable: Runnable? = null

    companion object {
        private const val SAME_BLOCK_DEDUPE_WINDOW_MS = 1_500L

        private val UNINSTALL_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        KeepAccessibilityServiceDebugState.update(applicationContext) { it.copy(isServiceConnected = true) }
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RoutineRuntimeEntryPoint::class.java,
        )
        launch {
            entryPoint.dataStore().data.collect { preferences ->
                cachedPrefs = CachedPreferences(
                    isKeep = preferences[PreferencesKey.IS_KEEP] ?: false,
                    lockTime = preferences[PreferencesKey.LOCK_TIME],
                    selectedAppPackages = preferences[PreferencesKey.SELECTED_APP_PACKAGES] ?: emptySet(),
                    preventUninstall = preferences[PreferencesKey.PREVENT_UNINSTALL] ?: true,
                    emergencyUnlockApps = preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] ?: emptySet(),
                    emergencyUnlockExpireTime = preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] ?: 0L,
                )
                KeepAccessibilityServiceDebugState.update(applicationContext) {
                    it.copy(
                        observedIsKeep = cachedPrefs.isKeep,
                        observedSelectedAppPackages = cachedPrefs.selectedAppPackages,
                        observedEmergencyUnlockApps = cachedPrefs.emergencyUnlockApps,
                    )
                }
                scheduleEmergencyUnlockExpiryCheck(cachedPrefs.emergencyUnlockExpireTime)
            }
        }
        launch {
            entryPoint.routineDao().fetchAll().collect { routineEntities ->
                cachedRoutines = routineEntities.map { it.toModel() }
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

        if (prefs.preventUninstall && isUninstallAttempt(packageName)) {
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

    private fun blockIfNeeded(
        packageName: String,
        prefs: CachedPreferences,
    ) {
        val blockRequest = resolveForegroundBlockRequest(
            packageName = packageName,
            prefs = AccessibilityBlockingPreferences(
                isKeep = prefs.isKeep,
                lockTime = prefs.lockTime,
                selectedAppPackages = prefs.selectedAppPackages,
            ),
            cachedRoutines = cachedRoutines,
            isEmergencyUnlocked = false,
            isDuplicateBlock = false,
        ) ?: return

        if (isDuplicateBlock(packageName = packageName, blockSource = blockRequest.blockSource)) return

        KeepAccessibilityServiceDebugState.update(applicationContext) {
            it.copy(lastLaunchedBlockPackage = packageName)
        }
        val intent = Intent(this, BlockActivity::class.java)
        intent.putExtra("package_name", packageName)
        intent.putExtra(BlockActivity.EXTRA_BLOCK_SOURCE, blockRequest.blockSource)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun isUninstallAttempt(eventPackageName: String): Boolean {
        if (eventPackageName !in UNINSTALL_PACKAGES) return false
        val rootNode = rootInActiveWindow ?: return false
        try {
            val idNodes = rootNode.findAccessibilityNodeInfosByText(BuildConfig.APPLICATION_ID)
            val foundById = !idNodes.isNullOrEmpty()
            idNodes?.forEach { it.recycle() }
            if (foundById) return true

            val nameNodes = rootNode.findAccessibilityNodeInfosByText(getString(R.string.app_name))
            val foundByName = !nameNodes.isNullOrEmpty()
            nameNodes?.forEach { it.recycle() }
            return foundByName
        } finally {
            rootNode.recycle()
        }
    }

    private fun dismissUninstallScreen() {
        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            val root = rootInActiveWindow
            val currentPackage = root?.packageName?.toString()
            root?.recycle()
            if (currentPackage != null && currentPackage in UNINSTALL_PACKAGES) {
                launchHomeScreen()
            }
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
                expireTimeMillis = prefs.emergencyUnlockExpireTime,
                nowMillis = now,
            )) {
            return true
        }
        return false
    }

    private fun cleanupExpiredEmergencyUnlock() {
        val prefs = cachedPrefs
        if (prefs.emergencyUnlockExpireTime > 0 &&
            System.currentTimeMillis() >= prefs.emergencyUnlockExpireTime &&
            isCleaningUp.compareAndSet(false, true)) {
            EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
            launch {
                dataStore.edit { preferences ->
                    preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
                    preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
                }
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

    private fun handleEmergencyUnlockExpired(expectedExpireTimeMillis: Long) {
        emergencyUnlockExpiryRunnable = null
        if (scheduledEmergencyUnlockExpireTime == expectedExpireTimeMillis) {
            scheduledEmergencyUnlockExpireTime = 0L
        }

        val prefs = cachedPrefs
        val foregroundPackage = currentForegroundPackage()
        val isForegroundStillEmergencyUnlocked = foregroundPackage?.let(::isEmergencyUnlocked) ?: false

        launch {
            val resolution = handleExpiredEmergencyUnlockForContext(
                context = this@KeepAccessibilityService,
                expectedExpireTimeMillis = expectedExpireTimeMillis,
                currentExpireTimeMillis = prefs.emergencyUnlockExpireTime,
                expiredUnlockedApps = prefs.emergencyUnlockApps,
                foregroundPackage = foregroundPackage,
                applicationId = BuildConfig.APPLICATION_ID,
                isForegroundStillEmergencyUnlocked = isForegroundStillEmergencyUnlocked,
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

internal suspend fun handleExpiredEmergencyUnlockForContext(
    context: Context,
    expectedExpireTimeMillis: Long,
    currentExpireTimeMillis: Long,
    expiredUnlockedApps: Set<String>,
    foregroundPackage: String?,
    applicationId: String,
    isForegroundStillEmergencyUnlocked: Boolean,
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
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
    }

    return resolution
}
