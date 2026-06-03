package com.uiery.keep.datastore

import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.receiver.PendingRoutineStartNotice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNoticeStoreTest {

    @Test
    fun pendingRoutineStartNoticesAreQueuedAndDrainedThroughRoutineNoticeBoundary() = runBlocking {
        val dataStore = FakeDataStore()
        val store = RoutineNoticeStore(dataStore)

        store.enqueuePendingRoutineStartNotice(PendingRoutineStartNotice("Morning routine started"))
        store.enqueuePendingRoutineStartNotice(PendingRoutineStartNotice("Evening routine started"))

        assertEquals(
            listOf("Morning routine started", "Evening routine started"),
            store.readPendingRoutineStartNoticeMessages(),
        )

        assertEquals("Morning routine started", store.drainNextPendingRoutineStartNotice())
        assertEquals(listOf("Evening routine started"), store.readPendingRoutineStartNoticeMessages())

        assertEquals("Evening routine started", store.drainNextPendingRoutineStartNotice())
        assertEquals(emptyList<String>(), store.readPendingRoutineStartNoticeMessages())
        assertNull(dataStore.snapshot()[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE])
    }

    @Test
    fun legacyPlainRoutineStartNoticeIsDrainedAndCleared() = runBlocking {
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE] = "Legacy notice"
        }
        val store = RoutineNoticeStore(dataStore)

        assertEquals(listOf("Legacy notice"), store.readPendingRoutineStartNoticeMessages())
        assertEquals("Legacy notice", store.drainNextPendingRoutineStartNotice())
        assertNull(dataStore.snapshot()[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE])
    }

    @Test
    fun alarmPermissionPromptFlagIsManagedThroughRoutineNoticeBoundary() = runBlocking {
        val dataStore = FakeDataStore()
        val store = RoutineNoticeStore(dataStore)

        assertFalse(store.hasShownAlarmPermissionPrompt())

        store.markAlarmPermissionPromptShown()
        assertTrue(store.hasShownAlarmPermissionPrompt())
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION])

        store.resetAlarmPermissionPrompt()
        assertFalse(store.hasShownAlarmPermissionPrompt())
        assertEquals(false, dataStore.snapshot()[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION])
    }
}
