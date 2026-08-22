package com.uiery.keep.feature.review

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ReviewPromptStateStore
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 수동 종료(`user_toggle_off`) 세션을 성공 세션으로 인정하기 위한 최소 지속 시간.
 *
 * 켜자마자 끄는 오조작까지 리뷰 후보로 만들지 않기 위한 하한이다. 타이머 완주 경로는
 * 완주 자체가 의도된 성공이므로 이 하한을 적용하지 않는다.
 *
 * 주의: 데이터 근거가 없는 초기 추정치다. `lock_session_end`에는 지속 시간 속성이 없어
 * 수동 세션 길이 분포를 조회할 수 없었다. 배포 +7일 readback에서
 * [SkipReason.BelowManualSessionDuration] 비중을 보고 조정한다.
 */
const val MANUAL_SESSION_MIN_MILLIS = 10L * 60 * 1000

/**
 * 성공 세션 종료 시점에 리뷰 프롬프트를 arm 할지 판단하는 단일 진입점.
 *
 * 2026-08 이전에는 이 판단이 `LockViewModel`의 타이머 완주 경로에만 있었다. 그 경로는
 * 전체 종료 세션의 3.8%뿐이라(GA4 79/2,054) 나머지 96%의 수동 종료 세션은 arm 평가
 * 자체가 일어나지 않았고, 30일 동안 `review_prompt_shown`이 3건에 그쳤다.
 * 두 경로가 같은 계약을 쓰도록 여기로 모은다.
 *
 * 계약:
 * - [minimumDurationMillis] 미만 세션은 성공 세션 카운터를 **올리지 않고**
 *   [SkipReason.BelowManualSessionDuration]으로 기록한다. 짧은 오조작이 누적 카운트를
 *   밀어 올리면 안 되기 때문이다.
 * - 하한을 통과하면 카운터를 올린 뒤, 방금 끝난 세션을 포함해 eligibility를 평가한다.
 *
 * 상세 계약은 `docs/REVIEW_PROMPT_LIFECYCLE.md`,
 * 근거는 `docs/RETENTION_DIAGNOSIS_2026_08.md` 2.4 참조.
 */
@Singleton
class ReviewPromptArmer
    @Inject
    constructor(
        private val blockingStateStore: BlockingStateStore,
        private val reviewPromptStateStore: ReviewPromptStateStore,
        private val reviewEligibility: ReviewEligibilityEvaluator,
        private val analytics: KeepAnalytics,
        private val clock: Clock,
    ) {
        suspend fun arm(
            sessionStartMillis: Long,
            isRoutine: Boolean,
            minimumDurationMillis: Long = 0L,
        ) {
            val now = clock.millis()
            val durationMillis = now - sessionStartMillis
            if (durationMillis < minimumDurationMillis) {
                analytics.reviewPromptSkipped(SkipReason.BelowManualSessionDuration.name)
                return
            }

            blockingStateStore.incrementSuccessfulSessionCount()
            val decision = reviewEligibility.evaluate(
                nowMs = now,
                durationMillis = durationMillis,
                isRoutine = isRoutine,
                includeCurrentSuccessfulSession = true,
            )
            when (decision) {
                is ReviewEligibilityDecision.Eligible -> {
                    reviewPromptStateStore.markPending()
                    analytics.reviewPromptEligible()
                }

                is ReviewEligibilityDecision.Ineligible -> {
                    analytics.reviewPromptSkipped(decision.reason.name)
                }
            }
        }
    }
