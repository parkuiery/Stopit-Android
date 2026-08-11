package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteLockRecommendationPolicyTest {
    @Test
    fun youtubeAppRecommendsItsCoreWebDomains() {
        val recommendations = WebsiteLockRecommendationPolicy.recommend(
            newlySelectedPackages = setOf("com.google.android.youtube"),
            alreadyBlockedDomains = emptySet(),
        )

        assertEquals(
            listOf(
                WebsiteLockRecommendation(
                    serviceName = "YouTube",
                    sourcePackage = "com.google.android.youtube",
                    domains = setOf(
                        DomainName("youtube.com"),
                        DomainName("youtu.be"),
                    ),
                ),
            ),
            recommendations,
        )
    }

    @Test
    fun alreadyBlockedDomainsAreNotRecommendedAgain() {
        val recommendations = WebsiteLockRecommendationPolicy.recommend(
            newlySelectedPackages = setOf("com.google.android.youtube"),
            alreadyBlockedDomains = setOf(DomainName("youtube.com")),
        )

        assertEquals(
            setOf(DomainName("youtu.be")),
            recommendations.single().domains,
        )
    }

    @Test
    fun serviceIsOmittedWhenEveryRecommendedDomainIsAlreadyBlocked() {
        val recommendations = WebsiteLockRecommendationPolicy.recommend(
            newlySelectedPackages = setOf("com.instagram.android"),
            alreadyBlockedDomains = setOf(DomainName("instagram.com")),
        )

        assertTrue(recommendations.isEmpty())
    }

    @Test
    fun unknownPackagesDoNotCreateGuessedRecommendations() {
        val recommendations = WebsiteLockRecommendationPolicy.recommend(
            newlySelectedPackages = setOf("com.example.privateapp"),
            alreadyBlockedDomains = emptySet(),
        )

        assertTrue(recommendations.isEmpty())
    }
}
