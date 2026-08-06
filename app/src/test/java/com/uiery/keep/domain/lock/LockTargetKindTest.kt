package com.uiery.keep.domain.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockTargetKindTest {
    @Test
    fun websiteOnlyLockIsNotDescribedAsAnAppLock() {
        assertEquals(
            LockTargetKind.Websites,
            LockTargetKind.of(hasApps = false, hasWebsites = true),
        )
    }

    @Test
    fun mixedLockNamesBothTargets() {
        assertEquals(
            LockTargetKind.AppsAndWebsites,
            LockTargetKind.of(hasApps = true, hasWebsites = true),
        )
    }

    @Test
    fun appLockAndTargetlessScreensKeepTheExistingAppWording() {
        assertEquals(LockTargetKind.Apps, LockTargetKind.of(hasApps = true, hasWebsites = false))
        // 루틴 잠금 화면은 대상 목록을 들고 있지 않다. 없는 대상을 새로 주장하면 안 된다.
        assertEquals(LockTargetKind.Apps, LockTargetKind.of(hasApps = false, hasWebsites = false))
    }
}
