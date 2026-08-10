package com.uiery.keep.ui.component

import com.uiery.keep.R
import com.uiery.keep.websiteblocking.WebsiteBlockingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // 필터가 네트워크 때문에 물러난 것도 "지금 막히지 않는다"는 같은 사실이다.
        assertEquals(
            R.string.website_blocking_unavailable_network,
            websiteBlockingUnavailableMessageRes(
                hasWebsiteTargets = true,
                status = WebsiteBlockingStatus.NetworkUnavailable,
            ),
        )
    }

    @Test
    fun onlyTheBreakageTheUserCanUndoGetsAButton() {
        // 동의 거부는 사용자가 그 자리에서 다시 허용할 수 있다. 자동 경로는 한 번 거부한
        // 잠금에 동의창을 다시 띄우지 않으므로, 도는 잠금을 되살릴 길은 이 버튼뿐이다.
        assertTrue(websiteBlockingRecoverableInPlace(WebsiteBlockingStatus.ConsentDenied))
    }

    @Test
    fun aBreakageTheUserCannotUndoGetsNoButton() {
        // 눌러도 아무 일이 없는 버튼은 없는 것만 못하다. 다른 VPN 과의 충돌은 남의 연결을
        // 끊는 일이라 잠금 시작 때 확인 대화상자로 따로 묻고, 네트워크 문제는 스스로 돌아온다.
        assertFalse(websiteBlockingRecoverableInPlace(WebsiteBlockingStatus.Unavailable))
        assertFalse(websiteBlockingRecoverableInPlace(WebsiteBlockingStatus.NetworkUnavailable))
        assertFalse(websiteBlockingRecoverableInPlace(WebsiteBlockingStatus.Active))
        assertFalse(websiteBlockingRecoverableInPlace(WebsiteBlockingStatus.Inactive))
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
