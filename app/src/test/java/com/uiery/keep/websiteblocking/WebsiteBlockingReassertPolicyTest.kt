package com.uiery.keep.websiteblocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈으로 돌아왔을 때 웹 차단을 다시 세워야 하는지.
 *
 * 홈의 판정 효과는 판정값이 *바뀔 때만* 돈다. 창 안에서 서비스만 죽으면 판정은 계속
 * `Running` 이라 아무 일도 일어나지 않고, 창이 끝날 때까지 웹이 열린 채 남는다.
 * 그렇다고 돌아올 때마다 무조건 다시 세우면 이미 서 있는 필터를 헛되이 다시 만들고,
 * 물러나 재시도 중인 서비스의 백오프와 싸우게 된다.
 */
class WebsiteBlockingReassertPolicyTest {
    @Test
    fun nothingStandingIsWhatAResumeIsFor() {
        assertTrue(
            WebsiteBlockingReassertPolicy.shouldReassertOnResume(WebsiteBlockingStatus.Inactive),
        )
    }

    @Test
    fun aStandingFilterIsLeftAlone() {
        // 다시 세우면 TUN 을 헛되이 다시 만들고 그 사이 질의가 흔들린다.
        assertFalse(
            WebsiteBlockingReassertPolicy.shouldReassertOnResume(WebsiteBlockingStatus.Active),
        )
    }

    @Test
    fun aServiceThatSteppedAsideIsRetryingOnItsOwn() {
        // 서비스는 살아서 백오프로 재시도 중이다. 홈이 끼어들면 그 백오프와 싸운다.
        assertFalse(
            WebsiteBlockingReassertPolicy.shouldReassertOnResume(
                WebsiteBlockingStatus.NetworkUnavailable,
            ),
        )
    }

    @Test
    fun aDeniedConsentIsNotAskedAgainByComingBackHome() {
        // 홈에 돌아올 때마다 시스템 동의창이 다시 뜨면 거부가 아니라 괴롭힘이 된다.
        assertFalse(
            WebsiteBlockingReassertPolicy.shouldReassertOnResume(
                WebsiteBlockingStatus.ConsentDenied,
            ),
        )
    }

    @Test
    fun anotherVpnHoldingTheSlotIsNotDisplacedWithoutAsking() {
        assertFalse(
            WebsiteBlockingReassertPolicy.shouldReassertOnResume(WebsiteBlockingStatus.Unavailable),
        )
    }
}
