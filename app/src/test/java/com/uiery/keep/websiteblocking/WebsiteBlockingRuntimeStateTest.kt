package com.uiery.keep.websiteblocking

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteBlockingRuntimeStateTest {
    @After
    fun reset() {
        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
    }

    @Test
    fun normalTeardownEndsTheActiveState() {
        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Active)

        WebsiteBlockingRuntimeState.clearActive()

        assertEquals(WebsiteBlockingStatus.Inactive, WebsiteBlockingRuntimeState.status.value)
    }

    @Test
    fun teardownDoesNotSwallowTheReasonBlockingNeverStarted() {
        // 서비스는 실패한 뒤에도 종료 경로를 지난다. 그때 상태를 Inactive 로 덮으면
        // 사용자는 왜 차단이 안 되는지 끝내 알 수 없다.
        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Unavailable)
        WebsiteBlockingRuntimeState.clearActive()
        assertEquals(WebsiteBlockingStatus.Unavailable, WebsiteBlockingRuntimeState.status.value)

        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
        WebsiteBlockingRuntimeState.clearActive()
        assertEquals(WebsiteBlockingStatus.ConsentDenied, WebsiteBlockingRuntimeState.status.value)
    }
}
