package com.uiery.keep.feature.home.component

import com.uiery.keep.R
import com.uiery.keep.domain.lock.LockTargetKind
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepOnMessageResTest {
    @Test
    fun homeStatusCopyFollowsWhatTheLockActuallyBlocks() {
        assertEquals(R.string.keep_on_message, keepOnMessageRes(LockTargetKind.Apps))
        assertEquals(R.string.keep_on_message_websites, keepOnMessageRes(LockTargetKind.Websites))
        assertEquals(
            R.string.keep_on_message_apps_and_websites,
            keepOnMessageRes(LockTargetKind.AppsAndWebsites),
        )
    }
}
