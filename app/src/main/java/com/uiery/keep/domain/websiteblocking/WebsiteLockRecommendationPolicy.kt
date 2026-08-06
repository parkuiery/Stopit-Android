package com.uiery.keep.domain.websiteblocking

data class WebsiteLockRecommendation(
    val serviceName: String,
    val sourcePackage: String,
    val domains: Set<DomainName>,
)

/**
 * 추천을 받아들이면 차단 목록이 늘어난다. 무엇이 추가되는지 이름으로 보여주지 않으면
 * 사용자는 자기가 무엇에 동의했는지 모른 채 잠금을 시작하게 된다.
 */
object WebsiteLockRecommendationCopy {
    fun serviceNames(recommendations: List<WebsiteLockRecommendation>): String =
        recommendations.joinToString { it.serviceName }

    fun domains(recommendations: List<WebsiteLockRecommendation>): String =
        recommendations
            .flatMap { recommendation -> recommendation.domains.map { it.value } }
            .distinct()
            .sorted()
            .joinToString()
}

object WebsiteLockRecommendationPolicy {
    private val presetsById = WebsiteLockPresetCatalog.popular.associateBy { it.id }

    private val recommendationsByPackage = mapOf(
        "com.google.android.youtube" to WebsiteLockRecommendation(
            serviceName = "YouTube",
            sourcePackage = "com.google.android.youtube",
            domains = presetsById.getValue("youtube").domains,
        ),
        "com.instagram.android" to WebsiteLockRecommendation(
            serviceName = "Instagram",
            sourcePackage = "com.instagram.android",
            domains = presetsById.getValue("instagram").domains,
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
