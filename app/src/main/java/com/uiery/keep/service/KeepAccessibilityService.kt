package com.uiery.keep.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.uiery.keep.BlockActivity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.timeNow
import com.uiery.keep.util.toDayOfWeekList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class KeepAccessibilityService : AccessibilityService(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        launch {
            val packageName = event?.packageName?.toString()
            val isKeep = this@KeepAccessibilityService.dataStore.data.map { preferences ->
                preferences[PreferencesKey.IS_KEEP]
            }.firstOrNull()
            val lockTime = this@KeepAccessibilityService.dataStore.data.map { preferences ->
                preferences[PreferencesKey.LOCK_TIME]
            }.firstOrNull()
            val isLockTime = lockTime?.let { LocalDateTime.now().isBefore(LocalDateTime.parse(it)) } ?: false
            val isShouldRoutineBlock = shouldRoutineBlock(packageName)

            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isKeep == true || isLockTime || isShouldRoutineBlock) {
                    val selectedAppPackage =
                        this@KeepAccessibilityService.dataStore.data.map { preferences ->
                            preferences[PreferencesKey.SELECTED_APP_PACKAGES].orEmpty()
                        }.firstOrNull()
                    if (selectedAppPackage?.contains(packageName) == true || isShouldRoutineBlock) {
                        val intent = Intent(this@KeepAccessibilityService, BlockActivity::class.java)
                        intent.putExtra("package_name", packageName)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
            }
        }
    }

    override fun onInterrupt() {

    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private suspend fun shouldRoutineBlock(packageName: String?): Boolean {
        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek

        val storeRoutines = this.dataStore.data.map { preferences ->
            preferences[PreferencesKey.ROUTINES]
        }.firstOrNull() ?: ""
        val routines = try {
            Json.decodeFromString<List<RoutineModel>>(storeRoutines)
        } catch (e: Exception) {
            emptyList()
        }

        return routines.any { routine ->
            if (!routine.isEnabled || routine.lockApplications?.contains(packageName) != true) {
                return@any false
            }

            val days = routine.repeatDays.toDayOfWeekList()

            val isDayMatched = currentDay in days
            val isTimeMatched = timeNow in routine.startTime .. routine.endTime
            return isDayMatched && isTimeMatched
        }
    }
}