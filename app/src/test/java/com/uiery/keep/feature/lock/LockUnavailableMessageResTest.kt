package com.uiery.keep.feature.lock

import com.uiery.keep.R
import com.uiery.keep.domain.lock.LockTargetKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LockUnavailableMessageResTest {
    @Test
    fun eachTargetKindGetsItsOwnPromise() {
        assertEquals(
            R.string.lock_screen_unavailable_message,
            lockUnavailableMessageRes(LockTargetKind.Apps, isMultiDay = false),
        )
        assertEquals(
            R.string.lock_screen_unavailable_message_websites,
            lockUnavailableMessageRes(LockTargetKind.Websites, isMultiDay = false),
        )
        assertEquals(
            R.string.lock_screen_unavailable_message_apps_and_websites,
            lockUnavailableMessageRes(LockTargetKind.AppsAndWebsites, isMultiDay = false),
        )
    }

    @Test
    fun multiDayLocksKeepTheirOwnCopyForEveryTargetKind() {
        assertEquals(
            R.string.lock_screen_unavailable_message_with_date,
            lockUnavailableMessageRes(LockTargetKind.Apps, isMultiDay = true),
        )
        assertEquals(
            R.string.lock_screen_unavailable_message_websites_with_date,
            lockUnavailableMessageRes(LockTargetKind.Websites, isMultiDay = true),
        )
        assertEquals(
            R.string.lock_screen_unavailable_message_apps_and_websites_with_date,
            lockUnavailableMessageRes(LockTargetKind.AppsAndWebsites, isMultiDay = true),
        )
    }

}
