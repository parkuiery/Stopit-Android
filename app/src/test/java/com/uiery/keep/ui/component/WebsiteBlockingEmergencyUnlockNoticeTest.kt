package com.uiery.keep.ui.component

import com.uiery.keep.websiteblocking.WebsiteBlockingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteBlockingEmergencyUnlockNoticeTest {
    @Test
    fun aStandingFilterTellsTheUserWebsitesWillNotOpen() {
        assertTrue(
            websiteBlockingEmergencyUnlockNoticeVisible(WebsiteBlockingStatus.Active),
        )
    }

    @Test
    fun aLockWithoutWebsitesSaysNothing() {
        // 앱만 잠근 사용자에게 웹 차단 이야기를 꺼내면 무슨 말인지 알 수 없다.
        assertFalse(
            websiteBlockingEmergencyUnlockNoticeVisible(WebsiteBlockingStatus.Inactive),
        )
    }

    @Test
    fun aFilterThatCouldNotStandDoesNotClaimWebsitesAreBlocked() {
        // 이 상태들에서는 WebsiteBlockingUnavailableBanner 가 이미 "웹사이트는 차단되고
        // 있지 않다"고 말한다. 같은 화면에서 반대되는 두 문장이 함께 뜨면 안 된다.
        for (status in listOf(
            WebsiteBlockingStatus.ConsentDenied,
            WebsiteBlockingStatus.Unavailable,
            WebsiteBlockingStatus.NetworkUnavailable,
        )) {
            assertFalse(
                "$status 에서는 웹사이트가 계속 차단된다고 말하면 안 된다",
                websiteBlockingEmergencyUnlockNoticeVisible(status),
            )
        }
    }
}
