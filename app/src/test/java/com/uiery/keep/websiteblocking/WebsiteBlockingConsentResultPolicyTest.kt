package com.uiery.keep.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteBlockingConsentResultPolicyTest {

    @Test
    fun grantedConsentWithAPendingLockStartsIt() {
        assertEquals(
            WebsiteBlockingConsentOutcome.StartPending,
            WebsiteBlockingConsentResultPolicy.outcome(
                granted = true,
                hasPendingStart = true,
            ),
        )
    }

    @Test
    fun deniedConsentIsRecordedSoTheScreenCanExplainIt() {
        assertEquals(
            WebsiteBlockingConsentOutcome.RecordDenied,
            WebsiteBlockingConsentResultPolicy.outcome(
                granted = false,
                hasPendingStart = true,
            ),
        )
    }

    @Test
    fun deniedConsentIsRecordedEvenWhenTheLockAlreadyEnded() {
        assertEquals(
            WebsiteBlockingConsentOutcome.RecordDenied,
            WebsiteBlockingConsentResultPolicy.outcome(
                granted = false,
                hasPendingStart = false,
            ),
        )
    }

    @Test
    fun grantedConsentWithoutAPendingLockIsNotRecordedAsDenial() {
        // 동의창이 떠 있는 동안 잠금이 끝난 경우다. 거부로 기록하면 허용을 누른 사용자에게
        // "권한이 없어 차단되지 않는다"고 말하게 되고, 그 상태가 같은 잠금의 동의 요청을
        // 억제해 웹 차단 없이 잠금이 진행된다.
        assertEquals(
            WebsiteBlockingConsentOutcome.Ignore,
            WebsiteBlockingConsentResultPolicy.outcome(
                granted = true,
                hasPendingStart = false,
            ),
        )
    }
}
