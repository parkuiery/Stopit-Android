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
    fun presetAddsEveryDomainInItsBundleInFrontOfWhatIsAlreadyThere() {
        val youtube = WebsiteLockPresetCatalog.popular.first { it.id == "youtube" }
        val initial = listOf("example.com")

        assertTrue(
            !WebsiteLockPresetSelectionPolicy.isSelected(
                selectedDomains = initial.toSet(),
                preset = youtube,
            ),
        )

        val selected = WebsiteLockPresetSelectionPolicy.add(
            selectedDomains = initial,
            preset = youtube,
        )

        assertEquals(listOf("youtube.com", "youtu.be", "example.com"), selected)
        assertTrue(
            WebsiteLockPresetSelectionPolicy.isSelected(
                selectedDomains = selected.toSet(),
                preset = youtube,
            ),
        )
    }

    @Test
    fun partlyRemovedPresetOnlyFillsBackTheMissingDomain() {
        val youtube = WebsiteLockPresetCatalog.popular.first { it.id == "youtube" }
        // 사용자가 youtu.be 만 지운 상태. 남은 youtube.com 은 사용자가 직접 넣었을 수도
        // 있으므로 프리셋이 건드리면 안 된다.
        val partial = listOf("youtube.com", "example.com")

        assertEquals(
            listOf("youtu.be"),
            WebsiteLockPresetSelectionPolicy.missingDomains(
                selectedDomains = partial.toSet(),
                preset = youtube,
            ),
        )
        assertEquals(
            listOf("youtu.be", "youtube.com", "example.com"),
            WebsiteLockPresetSelectionPolicy.add(
                selectedDomains = partial,
                preset = youtube,
            ),
        )
    }

    @Test
    fun addingAnAlreadyCompletePresetChangesNothing() {
        val instagram = WebsiteLockPresetCatalog.popular.first { it.id == "instagram" }
        val selected = listOf("instagram.com", "example.com")

        assertEquals(
            selected,
            WebsiteLockPresetSelectionPolicy.add(
                selectedDomains = selected,
                preset = instagram,
            ),
        )
    }
}
