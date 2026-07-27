package com.uiery.keep.domain.websiteblocking

data class WebsiteLockRecommendation(
    val serviceName: String,
    val sourcePackage: String,
    val domains: Set<DomainName>,
)

object WebsiteLockRecommendationPolicy {
    private val recommendationsByPackage = mapOf(
        "com.google.android.youtube" to WebsiteLockRecommendation(
            serviceName = "YouTube",
            sourcePackage = "com.google.android.youtube",
            domains = setOf(
                DomainName("youtube.com"),
                DomainName("youtu.be"),
            ),
        ),
        "com.instagram.android" to WebsiteLockRecommendation(
            serviceName = "Instagram",
            sourcePackage = "com.instagram.android",
            domains = setOf(DomainName("instagram.com")),
        ),
    )

    fun recommend(
        newlySelectedPackages: Set<String>,
        alreadyBlockedDomains: Set<DomainName>,
    ): List<WebsiteLockRecommendation> =
        newlySelectedPackages
            .sorted()
            .mapNotNull { packageName ->
                recommendationsByPackage[packageName]?.let { recommendation ->
                    recommendation.copy(
                        domains = recommendation.domains - alreadyBlockedDomains,
                    )
                }
            }
            .filter { it.domains.isNotEmpty() }
}
