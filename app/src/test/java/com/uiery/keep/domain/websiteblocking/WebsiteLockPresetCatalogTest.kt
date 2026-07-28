package com.uiery.keep.domain.websiteblocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteLockPresetCatalogTest {
    @Test
    fun popularPresetsUseVerifiedCanonicalDomains() {
        assertEquals(
            listOf(
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
            ),
            WebsiteLockPresetCatalog.popular,
        )
    }

    @Test
    fun presetIdsAndDomainsAreUniqueAndNormalized() {
        val presets = WebsiteLockPresetCatalog.popular
        val domains = presets.flatMap { it.domains }

        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertEquals(domains.size, domains.distinct().size)
        assertTrue(
            domains.all { domain ->
                DomainNamePolicy.normalize(domain.value) ==
                    DomainNameNormalizationResult.Valid(domain)
            },
        )
    }

    @Test
    fun presetSelectionAddsAndRemovesEveryDomainInItsBundle() {
        val youtube = WebsiteLockPresetCatalog.popular.first { it.id == "youtube" }
        val initial = setOf("example.com")

        assertTrue(
            !WebsiteLockPresetSelectionPolicy.isSelected(
                selectedDomains = initial,
                preset = youtube,
            ),
        )

        val selected = WebsiteLockPresetSelectionPolicy.updateSelection(
            selectedDomains = initial,
            preset = youtube,
            selected = true,
        )

        assertEquals(setOf("example.com", "youtube.com", "youtu.be"), selected)
        assertTrue(
            WebsiteLockPresetSelectionPolicy.isSelected(
                selectedDomains = selected,
                preset = youtube,
            ),
        )
        assertEquals(
            initial,
            WebsiteLockPresetSelectionPolicy.updateSelection(
                selectedDomains = selected,
                preset = youtube,
                selected = false,
            ),
        )
    }
}
