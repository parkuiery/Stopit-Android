package com.uiery.keep.websiteblocking

import android.content.Context
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingAsserter
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingDomainSetPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [WebsiteBlockingAsserter] 의 Android 구현. 계약과 배경은 인터페이스 쪽에 있다. */
@Singleton
class AndroidWebsiteBlockingAsserter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WebsiteBlockingAsserter {
        override fun assertAfterConsent(
            selectedWebDomains: Set<String>,
            stopAtEpochMillis: Long?,
        ): Boolean {
            val domains = WebsiteBlockingDomainSetPolicy.normalize(selectedWebDomains)
            // 막을 것이 없으면 세울 것도 없다. 경고도 애초에 뜨지 않는 상태다.
            if (domains.isEmpty()) return false

            if (VpnService.prepare(context) != null) {
                // 경고가 다시 뜨는 상태로 되돌린다. 배너만 사라지고 차단은 서지 않는 것이
                // 가장 나쁜 결과다.
                WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
                return false
            }

            context.startWebsiteBlocking(domains, stopAtEpochMillis)
            return true
        }
    }

/**
 * 서비스를 세우는 단 하나의 자리. 컨트롤러와 asserter 가 같은 인텐트를 쓰도록 여기 둔다.
 * 두 벌이 되면 한쪽만 고쳐진 채 남는다.
 */
internal fun Context.startWebsiteBlocking(
    domains: Set<DomainName>,
    stopAtEpochMillis: Long?,
) {
    ContextCompat.startForegroundService(
        this,
        KeepDnsVpnService.startIntent(
            context = this,
            domains = domains,
            stopAtEpochMillis = stopAtEpochMillis,
        ),
    )
}
