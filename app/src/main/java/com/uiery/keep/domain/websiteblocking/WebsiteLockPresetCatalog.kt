package com.uiery.keep.domain.websiteblocking

data class WebsiteLockPreset(
    val id: String,
    val serviceName: String,
    val domains: Set<DomainName>,
)

object WebsiteLockPresetCatalog {
    val popular: List<WebsiteLockPreset> = listOf(
        WebsiteLockPreset(
            id = "youtube",
            serviceName = "YouTube",
            domains = setOf(
                DomainName("youtube.com"),
                DomainName("youtu.be"),
            ),
        ),
        WebsiteLockPreset(
            id = "instagram",
            serviceName = "Instagram",
            domains = setOf(DomainName("instagram.com")),
        ),
        WebsiteLockPreset(
            id = "tiktok",
            serviceName = "TikTok",
            domains = setOf(DomainName("tiktok.com")),
        ),
        WebsiteLockPreset(
            id = "x",
            serviceName = "X",
            domains = setOf(
                DomainName("x.com"),
                DomainName("twitter.com"),
            ),
        ),
        WebsiteLockPreset(
            id = "facebook",
            serviceName = "Facebook",
            domains = setOf(DomainName("facebook.com")),
        ),
        WebsiteLockPreset(
            id = "netflix",
            serviceName = "Netflix",
            domains = setOf(DomainName("netflix.com")),
        ),
        WebsiteLockPreset(
            id = "twitch",
            serviceName = "Twitch",
            domains = setOf(DomainName("twitch.tv")),
        ),
        WebsiteLockPreset(
            id = "reddit",
            serviceName = "Reddit",
            domains = setOf(DomainName("reddit.com")),
        ),
        WebsiteLockPreset(
            id = "naver",
            serviceName = "NAVER",
            domains = setOf(DomainName("naver.com")),
        ),
        WebsiteLockPreset(
            id = "coupang",
            serviceName = "쿠팡",
            domains = setOf(DomainName("coupang.com")),
        ),
    )
}

object WebsiteLockPresetSelectionPolicy {
    fun isSelected(
        selectedDomains: Set<String>,
        preset: WebsiteLockPreset,
    ): Boolean = preset.domains.all { domain -> domain.value in selectedDomains }

    fun updateSelection(
        selectedDomains: Set<String>,
        preset: WebsiteLockPreset,
        selected: Boolean,
    ): Set<String> {
        val presetDomains = preset.domains.mapTo(mutableSetOf()) { it.value }
        return if (selected) {
            selectedDomains + presetDomains
        } else {
            selectedDomains - presetDomains
        }
    }
}
