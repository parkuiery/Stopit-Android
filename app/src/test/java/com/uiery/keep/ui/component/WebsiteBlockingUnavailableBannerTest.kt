package com.uiery.keep.ui.component

import com.uiery.keep.R
import com.uiery.keep.websiteblocking.WebsiteBlockingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteBlockingUnavailableBannerTest {
    @Test
    fun aLockThatCannotBlockWebsitesSaysWhy() {
        assertEquals(
            R.string.website_blocking_unavailable_consent,
            websiteBlockingUnavailableMessageRes(
                hasWebsiteTargets = true,
                status = WebsiteBlockingStatus.ConsentDenied,
            ),
        )
        assertEquals(
            R.string.website_blocking_unavailable_conflict,
            websiteBlockingUnavailableMessageRes(
                hasWebsiteTargets = true,
                status = WebsiteBlockingStatus.Unavailable,
            ),
        )
    }

    @Test
    fun aWorkingOrIrrelevantLockStaysQuiet() {
        assertNull(
            websiteBlockingUnavailableMessageRes(
                hasWebsiteTargets = true,
                status = WebsiteBlockingStatus.Active,
            ),
        )
        assertNull(
            websiteBlockingUnavailableMessageRes(
                hasWebsiteTargets = true,
                status = WebsiteBlockingStatus.Inactive,
            ),
        )
        // 앱만 잠근 사용자에게 웹 차단 실패를 알리면 무슨 말인지 알 수 없다.
        assertNull(
            websiteBlockingUnavailableMessageRes(
                hasWebsiteTargets = false,
                status = WebsiteBlockingStatus.Unavailable,
            ),
        )
    }
}
