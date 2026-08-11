package com.uiery.keep.websiteblocking

/**
 * 시스템 VPN 동의창이 닫힌 뒤 무엇을 할지.
 *
 * 허용 여부만으로 판단하면 안 된다. 동의창이 떠 있는 동안 잠금이 끝나면 시작할 대상이
 * 사라지는데, 그 경우를 거부와 같이 취급하면 **허용을 누른 사용자가 거부한 것으로 기록된다.**
 * 그러면 [WebsiteBlockingStatus.ConsentDenied] 가 남아 "권한이 없어 차단되지 않는다"는
 * 잘못된 안내가 뜨고, 그 상태는 같은 잠금에서 동의창을 다시 띄우지 않도록 억제하는 데도
 * 쓰이므로 웹 차단 없이 잠금이 진행된다.
 *
 * 허용을 받았는데 시작할 대상이 없으면 아무것도 기록하지 않는 것이 맞다. 동의는 이미
 * 시스템에 남아 있어서, 다음 잠금은 동의창 없이 곧바로 선다.
 */
enum class WebsiteBlockingConsentOutcome {
    /** 허용받았고 시작할 대상도 있다. */
    StartPending,

    /** 거부당했다. 잠금은 유지하되 웹 차단이 서지 못한다고 알린다. */
    RecordDenied,

    /** 허용은 받았지만 그 사이 시작할 대상이 사라졌다. 아무것도 기록하지 않는다. */
    Ignore,
}

object WebsiteBlockingConsentResultPolicy {
    fun outcome(
        granted: Boolean,
        hasPendingStart: Boolean,
    ): WebsiteBlockingConsentOutcome = when {
        !granted -> WebsiteBlockingConsentOutcome.RecordDenied
        hasPendingStart -> WebsiteBlockingConsentOutcome.StartPending
        else -> WebsiteBlockingConsentOutcome.Ignore
    }
}
