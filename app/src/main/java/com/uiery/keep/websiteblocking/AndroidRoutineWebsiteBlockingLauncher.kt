package com.uiery.keep.websiteblocking

import android.content.Context
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.uiery.keep.analytics.AnalyticsWebsiteBlockingRoutineOutcome
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingApplyAction
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingApplyPolicy
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingLauncher
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingPolicy
import com.uiery.keep.domain.websiteblocking.RoutineWebsiteBlockingTrigger
import com.uiery.keep.domain.websiteblocking.toRoutineWebsiteWindows
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 루틴 시간대의 웹 차단을 실제로 세우고 내리는 자리.
 *
 * 루틴은 화면 없이 시작된다. 이 판정이 알람 수신자 안에만 있으면 알람이 유일한 계기가 되고,
 * 그 한 번이 실패했을 때(지연·누락·재부팅) 창 안인데도 웹이 열린 채 남는다. 그래서 판정을
 * 여기로 꺼내 부팅 직후와 루틴 저장 시점이 같은 규칙을 다시 돌릴 수 있게 한다.
 *
 * 시스템 VPN 동의창은 화면에서만 띄울 수 있으므로, 동의가 없으면 상태만 남기고 물러난다.
 */
@Singleton
class AndroidRoutineWebsiteBlockingLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val analytics: KeepAnalytics,
    ) : RoutineWebsiteBlockingLauncher {
        override fun apply(
            routines: List<RoutineModel>,
            trigger: RoutineWebsiteBlockingTrigger,
        ) {
            val windows = routines.toRoutineWebsiteWindows()
            val session = RoutineWebsiteBlockingPolicy.resolveSession(windows)
            val hasVpnConsent = VpnService.prepare(context) == null
            AppLogger.debug(
                DIAGNOSTIC_TAG,
                "routine_web total=${windows.size}" +
                    " active=${windows.count { it.isEnabled && it.isActiveNow }}" +
                    " withSites=${windows.count { it.websites.isNotEmpty() }}" +
                    " session=${session != null}" +
                    " consent=$hasVpnConsent",
            )

            when (
                val action = RoutineWebsiteBlockingApplyPolicy.decide(
                    session = session,
                    hasVpnConsent = hasVpnConsent,
                    isBlockingStanding =
                        WebsiteBlockingRuntimeState.status.value != WebsiteBlockingStatus.Inactive,
                )
            ) {
                // 아무것도 하지 않은 회차는 세지 않는다. 알람과 부팅은 창 밖에서도 돌기 때문에,
                // 이걸 세면 "판정이 몇 번 돌았나"가 지표를 덮어 실제 세션 신호가 묻힌다.
                RoutineWebsiteBlockingApplyAction.DoNothing -> Unit

                RoutineWebsiteBlockingApplyAction.StopBlocking -> {
                    context.startService(KeepDnsVpnService.stopIntent(context))
                    WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
                    report(AnalyticsWebsiteBlockingRoutineOutcome.STOPPED, trigger)
                }

                RoutineWebsiteBlockingApplyAction.ReportConsentMissing -> {
                    WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
                    // 가장 조용한 실패다. 루틴 창은 열렸는데 동의가 없어 웹은 그대로 뚫려 있고,
                    // 수신자에서는 동의창을 띄울 수도 없다. 사용자가 앱을 열지 않으면 아무도
                    // 모른다. 이 회차를 세지 않으면 우리도 모른다.
                    report(AnalyticsWebsiteBlockingRoutineOutcome.CONSENT_MISSING, trigger)
                }

                is RoutineWebsiteBlockingApplyAction.StartBlocking -> {
                    ContextCompat.startForegroundService(
                        context,
                        KeepDnsVpnService.startIntent(
                            context = context,
                            domains = action.session.domains,
                            stopAtEpochMillis = action.session.stopAtEpochMillis,
                        ),
                    )
                    report(AnalyticsWebsiteBlockingRoutineOutcome.STARTED, trigger)
                }
            }
        }

        private fun report(
            outcome: String,
            trigger: RoutineWebsiteBlockingTrigger,
        ) {
            analytics.trackWebsiteBlockingRoutineSession(
                outcome = outcome,
                trigger = trigger.name.lowercase(),
            )
        }

        private companion object {
            const val DIAGNOSTIC_TAG = "KeepRoutineWeb"
        }
    }
