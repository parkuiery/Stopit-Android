package com.uiery.keep.domain.websiteblocking

/**
 * 동의를 방금 받은 화면이 두드리는 자리. 필터를 실제로 세우는 일을 화면 밖으로 꺼낸다.
 *
 * 이 경로가 없던 동안 잠금 화면의 웹 차단 경고에는 복구 버튼을 붙일 수 없었다. 권한만 받아서는
 * 필터가 서지 않고, 실제로 세우는 판정 효과가 `WebsiteBlockingVpnController` 안에 있는데 그
 * 컨트롤러는 홈에만 있었기 때문이다. 홈은 `resumeCount` 를 올려 자기 효과를 다시 돌리는 방식으로
 * 우회했지만, 그건 컨트롤러를 쥔 화면에서만 가능한 수법이다.
 *
 * 그래서 시간 잠금으로 들어간 사용자는 잠금 화면에 머무르며 "웹사이트는 차단되고 있지 않아요"를
 * 보면서도 되살릴 방법이 없었다. 자동 경로는 한 번 거부한 잠금에 동의창을 다시 띄우지 않는다.
 *
 * 시스템 VPN 동의창 자체는 액티비티 결과로만 받을 수 있어 화면에 남는다. 여기로 내리는 것은
 * 동의를 받은 **다음**, 그 판정대로 필터를 세우는 부분이다.
 */
fun interface WebsiteBlockingAsserter {
    /**
     * 지금 동의가 있으면 [selectedWebDomains] 를 막기 시작한다.
     *
     * 세우지 못했으면 **false 를 돌려주고 런타임 상태를 되돌린다.** 배너의 복구 버튼은 눌리는
     * 즉시 상태를 `Inactive` 로 낙관적으로 지우는데, 그대로 두면 경고만 사라지고 차단은 서지
     * 않아 사용자가 막히고 있다고 잘못 믿게 된다. 아무 말도 하지 않는 것보다 나쁘다.
     *
     * @param stopAtEpochMillis 마감 없는 잠금이면 null. 과거 시각을 넘기면 서비스가 서자마자
     *   멈추므로 호출자가 미래인지 확인해서 넘긴다.
     */
    fun assertAfterConsent(
        selectedWebDomains: Set<String>,
        stopAtEpochMillis: Long?,
    ): Boolean

    companion object {
        /** 웹 차단을 건드리지 않는 구현. 테스트와 기본값용. */
        val None = WebsiteBlockingAsserter { _, _ -> false }
    }
}
