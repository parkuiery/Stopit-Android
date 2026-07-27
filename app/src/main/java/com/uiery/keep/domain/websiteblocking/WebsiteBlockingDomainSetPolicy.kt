package com.uiery.keep.domain.websiteblocking

object WebsiteBlockingDomainSetPolicy {
    fun normalize(domains: Collection<String>): Set<DomainName> =
        domains.mapNotNull { rawDomain ->
            (DomainNamePolicy.normalize(rawDomain) as? DomainNameNormalizationResult.Valid)?.domain
        }.toSet()
}
