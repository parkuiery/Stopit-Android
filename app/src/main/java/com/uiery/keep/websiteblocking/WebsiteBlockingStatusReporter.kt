package com.uiery.keep.websiteblocking

import com.uiery.keep.analytics.KeepAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 웹 차단 필터의 상태 전환을 프로세스 단위로 한 번만 기록한다.
 *
 * 처음에는 이 관찰을 홈 화면의 컨트롤러에 두었는데, 그러면 홈이 화면에 떠 있는 동안만 세게
 * 된다. 정작 놓치면 안 되는 전환은 사용자가 홈을 보고 있지 않을 때 일어난다 — 루틴 알람이
 * 세운 세션이 다른 VPN 에 밀려 내려가거나, 업스트림 DNS 가 죽어 필터가 스스로 물러나거나,
 * 잠금 화면에 머무는 동안 동의가 회수되는 경우다.
 *
 * [WebsiteBlockingRuntimeState] 는 홈 컨트롤러·루틴 런처·VPN 서비스가 모두 지나는 길목이고
 * 같은 프로세스에서 공유된다. 그래서 여기 한 곳에서 관찰하면 경로를 따지지 않고 전부 덮인다.
 *
 * 상태 자체가 무슨 일이 났는지 말해 주므로(ConsentDenied / Unavailable / NetworkUnavailable)
 * 별도의 surface 값을 싣지 않는다. 어떤 화면이 켜져 있었는지는 이 사건의 성질이 아니다.
 */
@Singleton
class WebsiteBlockingStatusReporter
    @Inject
    constructor(
        private val analytics: KeepAnalytics,
    ) {
        /**
         * [scope] 는 프로세스가 살아 있는 동안 유지되어야 한다. 화면 수명에 묶으면 이 클래스가
         * 존재하는 이유가 없어진다.
         */
        fun start(scope: CoroutineScope) {
            scope.launch {
                WebsiteBlockingRuntimeState.status
                    // 첫 방출은 현재 값이지 전환이 아니다. 프로세스가 재시작될 때마다 이걸
                    // 실으면 체류가 전환으로 둔갑한다.
                    .drop(1)
                    .distinctUntilChanged()
                    .collect { status ->
                        analytics.trackWebsiteBlockingStatusChanged(status.name)
                    }
            }
        }
    }
