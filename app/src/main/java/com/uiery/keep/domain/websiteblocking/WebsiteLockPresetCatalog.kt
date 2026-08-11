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

/**
 * 프리셋은 선택 상태를 소유하지 않는다. 담긴 도메인 목록만이 유일한 상태이고, 프리셋은
 * 거기에 도메인을 넣는 지름길일 뿐이다.
 *
 * 프리셋이 체크박스였을 때는 한 프리셋의 도메인을 하나만 지워도 체크가 풀렸고, 그 상태에서
 * 다시 누르면 사용자가 직접 입력했을지 모르는 나머지 도메인까지 함께 지워졌다. 담기만 하고
 * 빼는 일은 선택 목록에 맡기면 그런 거짓 상태가 생기지 않는다.
 */
object WebsiteLockPresetSelectionPolicy {
    /** 프리셋의 도메인이 이미 전부 담겨 있는가. 더 담을 것이 없다는 뜻이다. */
    fun isSelected(
        selectedDomains: Set<String>,
        preset: WebsiteLockPreset,
    ): Boolean = preset.domains.all { domain -> domain.value in selectedDomains }

    /** 아직 담기지 않은 도메인. 일부만 지워진 프리셋은 빈 자리만 채운다. */
    fun missingDomains(
        selectedDomains: Set<String>,
        preset: WebsiteLockPreset,
    ): List<String> = preset.domains
        .map { it.value }
        .filterNot { it in selectedDomains }

    /**
     * 부족한 도메인을 목록 맨 앞에 담는다. 방금 담긴 것이 입력창 바로 아래에 보여야
     * 눌렀다는 사실이 확인된다.
     */
    fun add(
        selectedDomains: List<String>,
        preset: WebsiteLockPreset,
    ): List<String> = missingDomains(selectedDomains.toSet(), preset) + selectedDomains
}
