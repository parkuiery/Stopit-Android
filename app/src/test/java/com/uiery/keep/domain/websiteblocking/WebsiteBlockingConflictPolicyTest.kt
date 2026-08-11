package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteBlockingConflictPolicyTest {
    @Test
    fun anotherAppsVpnIsNeverDisconnectedWithoutAsking() {
        // 실기기에서 확인한 동작: 동의를 이미 받은 상태면 시스템은 아무것도 묻지 않고
        // 상대 VPN 을 내려버리고, 그 VPN 은 잠금이 끝나도 돌아오지 않는다.
        assertTrue(
            WebsiteBlockingConflictPolicy.shouldAskBeforeDisplacing(
                status = WebsiteBlockingOwnership.NotOwnedByKeep,
                otherVpnActive = true,
                displacementApproved = false,
            ),
        )
    }

    @Test
    fun theSameLockDoesNotAskTwice() {
        assertFalse(
            WebsiteBlockingConflictPolicy.shouldAskBeforeDisplacing(
                status = WebsiteBlockingOwnership.NotOwnedByKeep,
                otherVpnActive = true,
                displacementApproved = true,
            ),
        )
    }

    @Test
    fun nothingToDisplaceMeansNoQuestion() {
        assertFalse(
            WebsiteBlockingConflictPolicy.shouldAskBeforeDisplacing(
                status = WebsiteBlockingOwnership.NotOwnedByKeep,
                otherVpnActive = false,
                displacementApproved = false,
            ),
        )
    }

    @Test
    fun ourOwnRunningVpnIsNotMistakenForSomeoneElses() {
        // 우리 VPN 도 시스템에는 VPN 네트워크로 보인다. 이것을 남의 것으로 읽으면
        // 차단이 도는 내내 "다른 VPN을 끊을까요?" 를 스스로에게 묻게 된다.
        assertFalse(
            WebsiteBlockingConflictPolicy.shouldAskBeforeDisplacing(
                status = WebsiteBlockingOwnership.OwnedByKeep,
                otherVpnActive = true,
                displacementApproved = false,
            ),
        )
    }
}
