package com.uiery.keep.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.preferences.core.edit
import com.uiery.keep.BlockActivity
import com.uiery.keep.BuildConfig
import com.uiery.keep.R
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
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
        val routines: String = "",
        val preventUninstall: Boolean = true,
        val emergencyUnlockApps: Set<String> = emptySet(),
        val emergencyUnlockExpireTime: Long = 0L,
    )

    @Volatile
    private var cachedPrefs = CachedPreferences()

    private val handler = Handler(Looper.getMainLooper())
    private val isCleaningUp = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        private val UNINSTALL_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        launch {
            dataStore.data.collect { preferences ->
                cachedPrefs = CachedPreferences(
                    isKeep = preferences[PreferencesKey.IS_KEEP] ?: false,
                    lockTime = preferences[PreferencesKey.LOCK_TIME],
                    selectedAppPackages = preferences[PreferencesKey.SELECTED_APP_PACKAGES] ?: emptySet(),
                    routines = preferences[PreferencesKey.ROUTINES] ?: "",
                    preventUninstall = preferences[PreferencesKey.PREVENT_UNINSTALL] ?: true,
                    emergencyUnlockApps = preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] ?: emptySet(),
                    emergencyUnlockExpireTime = preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] ?: 0L,
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val prefs = cachedPrefs

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        cleanupExpiredEmergencyUnlock()
        if (isEmergencyUnlocked(packageName)) return

        val isLockTime = prefs.lockTime?.let {
            runCatching {
                LocalDateTime.now().isBefore(LocalDateTime.parse(it))
            }.getOrDefault(false)
        } ?: false
        val isShouldRoutineBlock = shouldRoutineBlock(packageName, prefs.routines)
        val isBlocking = prefs.isKeep || isLockTime || isShouldRoutineBlock

        if (prefs.preventUninstall && isUninstallAttempt(packageName)) {
            dismissUninstallScreen()
            return
        }

        if (isBlocking) {
            if (prefs.selectedAppPackages.contains(packageName) || isShouldRoutineBlock) {
                val intent = Intent(this, BlockActivity::class.java)
                intent.putExtra("package_name", packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        job.cancel()
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
        val snapshot = EmergencyUnlockState.current
        if (snapshot.unlockedApps.contains(packageName) &&
            System.currentTimeMillis() < snapshot.expireTimeMillis) {
            return true
        }
        val prefs = cachedPrefs
        if (prefs.emergencyUnlockApps.contains(packageName) &&
            System.currentTimeMillis() < prefs.emergencyUnlockExpireTime) {
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

    private fun shouldRoutineBlock(packageName: String, routinesJson: String): Boolean {
        val routines = try {
            Json.decodeFromString<List<RoutineModel>>(routinesJson)
        } catch (e: Exception) {
            emptyList()
        }

        return routines.any { routine ->
            if (!routine.isEnabled || routine.lockApplications?.contains(packageName) != true) {
                return@any false
            }

            isRoutineActiveNow(
                startTime = routine.startTime,
                endTime = routine.endTime,
                repeatDays = routine.repeatDays.toDayOfWeekList(),
            )
        }
    }
}
