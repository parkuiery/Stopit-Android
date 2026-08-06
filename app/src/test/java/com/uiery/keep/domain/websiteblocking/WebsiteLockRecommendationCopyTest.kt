package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteLockRecommendationCopyTest {
    private val youtube = WebsiteLockRecommendation(
        serviceName = "YouTube",
        sourcePackage = "com.google.android.youtube",
        domains = setOf(DomainName("youtube.com"), DomainName("youtu.be")),
    )
    private val instagram = WebsiteLockRecommendation(
        serviceName = "Instagram",
        sourcePackage = "com.instagram.android",
        domains = setOf(DomainName("instagram.com")),
    )

    @Test
    fun everyServiceBehindTheProposalIsNamed() {
        assertEquals(
            "YouTube, Instagram",
            WebsiteLockRecommendationCopy.serviceNames(listOf(youtube, instagram)),
        )
    }

    @Test
    fun exactlyWhatGetsBlockedIsListedInAStableOrder() {
        // 수락 한 번으로 늘어나는 차단 대상이다. 순서가 흔들리면 같은 제안이 매번 달라 보인다.
        assertEquals(
            "instagram.com, youtu.be, youtube.com",
            WebsiteLockRecommendationCopy.domains(listOf(youtube, instagram)),
        )
    }

    @Test
    fun theSameDomainIsNotListedTwice() {
        val overlapping = youtube.copy(serviceName = "YouTube Music")
        assertEquals(
            "youtu.be, youtube.com",
            WebsiteLockRecommendationCopy.domains(listOf(youtube, overlapping)),
        )
    }
}
