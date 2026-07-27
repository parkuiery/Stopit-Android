package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteBlockingDomainSetPolicyTest {
    @Test
    fun normalizesAndDeduplicatesConfiguredDomains() {
        assertEquals(
            setOf(
                DomainName("youtube.com"),
                DomainName("instagram.com"),
            ),
            WebsiteBlockingDomainSetPolicy.normalize(
                setOf(
                    "https://www.YouTube.com/watch?v=1",
                    "youtube.com",
                    "instagram.com",
                    "localhost",
                ),
            ),
        )
    }

    @Test
    fun emptyOrInvalidConfigurationDoesNotCreateFallbackBlockingTarget() {
        assertEquals(
            emptySet<DomainName>(),
            WebsiteBlockingDomainSetPolicy.normalize(setOf("", "localhost")),
        )
    }
}
