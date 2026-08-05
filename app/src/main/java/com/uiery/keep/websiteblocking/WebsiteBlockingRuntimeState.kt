package com.uiery.keep.websiteblocking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 웹 차단이 실제로 걸려 있는지. 잠금이 켜졌다는 사실과 웹사이트가 실제로 막히고 있다는
 * 사실은 다르다. 동의를 거부했거나 다른 VPN 이 슬롯을 쥐고 있으면 잠금은 유지되지만
 * 웹사이트는 그대로 열린다. 그 차이를 사용자에게 숨기지 않기 위한 상태다.
 */
enum class WebsiteBlockingStatus {
    /** 웹 차단을 요구하지 않는 상태. */
    Inactive,

    /** DNS 필터가 실제로 서고 있다. */
    Active,

    /** 시스템 VPN 동의를 받지 못했다. */
    ConsentDenied,

    /** 동의는 있으나 VPN 을 세우지 못했다. 다른 VPN 이 슬롯을 쥔 경우가 대표적이다. */
    Unavailable,

    /**
     * 네트워크 사정으로 필터가 스스로 물러났다. 업스트림 DNS 가 응답하지 않을 때 차단을
     * 유지하면 인터넷 전체가 막히므로 fail-open 이 맞지만, 조용히 물러나면 사용자는
     * 막히고 있다고 믿은 채로 남는다.
     */
    NetworkUnavailable,
}

/**
 * VPN 서비스와 화면이 같은 프로세스에서 즉시 공유하는 상태.
 * [com.uiery.keep.service.EmergencyUnlockState] 와 같은 이유로 메모리에만 둔다.
 */
object WebsiteBlockingRuntimeState {
    private val mutableStatus = MutableStateFlow(WebsiteBlockingStatus.Inactive)
    val status: StateFlow<WebsiteBlockingStatus> = mutableStatus.asStateFlow()

    fun update(status: WebsiteBlockingStatus) {
        mutableStatus.value = status
    }

    /**
     * 서비스가 정상 종료될 때 쓴다. 동의 거부나 VPN 실패로 이미 남겨 둔 경고를
     * 종료 처리가 덮어써 버리면 사용자는 아무 설명도 받지 못한다.
     */
    fun clearActive() {
        if (mutableStatus.value == WebsiteBlockingStatus.Active) {
            mutableStatus.value = WebsiteBlockingStatus.Inactive
        }
    }
}
