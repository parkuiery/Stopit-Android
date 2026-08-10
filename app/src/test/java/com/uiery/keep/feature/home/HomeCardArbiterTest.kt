package com.uiery.keep.feature.home

import com.uiery.keep.domain.usageinsight.UsageInsight
import com.uiery.keep.feature.home.component.UsageInsightCardUiState
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardArbiterTest {

    private val resumeCard = true
    private val insight = UsageInsightCardUiState.Insight(
        insight = UsageInsight.NightOwl(
            packageName = "com.instagram.android",
            nightsCount = 3,
            avgNightUsage = Duration.ofMinutes(40),
        ),
        appLabel = "Instagram",
    )

    @Test
    fun `locking in progress asks for nothing new`() {
        assertEquals(
            HomeCard.None,
            decideHomeCard(
                isKeep = true,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = false,
                showFirstLockActivationCta = true,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
        assertEquals(
            HomeCard.None,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = true,
                hasFirstPromiseResumeCard = false,
                showFirstLockActivationCta = true,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
    }

    @Test
    fun `a broken promise is still reported while a lock is running`() {
        // 재개 카드는 제안이 아니라 고장 알림이다. 무관한 잠금이 돈다는 이유로 숨기면 약속은
        // 조용히 실패하고 사용자는 이유를 알 수 없다.
        assertEquals(
            HomeCard.FirstPromiseResume,
            decideHomeCard(
                isKeep = true,
                hasActiveTimedLock = true,
                hasFirstPromiseResumeCard = resumeCard,
                showFirstLockActivationCta = true,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
    }

    @Test
    fun `only one card wins when every condition is met at once`() {
        assertEquals(
            HomeCard.FirstPromiseResume,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = true,
                showFirstLockActivationCta = true,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
    }

    @Test
    fun `first lock experience comes before deeper suggestions`() {
        assertEquals(
            HomeCard.FirstLockActivation,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = false,
                showFirstLockActivationCta = true,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
    }

    @Test
    fun `usage insight fills in once the activation path is clear`() {
        assertEquals(
            HomeCard.UsageInsight,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = false,
                showFirstLockActivationCta = false,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
    }

    @Test
    fun `a web blocking warning takes the top slot away from suggestions`() {
        // 경고 배너와 제안 카드가 함께 쌓이면 상단 묶음이 두 배가 되어 아래 잠금 스위치를
        // 화면 밖으로 밀어낸다. 화면의 주 동작이 부차적 기능의 안내에 밀려나면 안 된다.
        assertEquals(
            HomeCard.None,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = false,
                showFirstLockActivationCta = true,
                usageInsightCard = insight,
                hasWebBlockingWarning = true,
            ),
        )
    }

    @Test
    fun `a web blocking warning takes the top slot away from every card`() {
        // 재개 카드도 고장이지만 그것은 "아직 시작되지 않은 약속"이라 다음 방문에 다시
        // 올라온다. 경고는 지금 이 순간의 거짓("막히고 있다")을 바로잡는 말이라 늦게 알수록
        // 손해가 쌓인다. 둘을 함께 세우면 상단이 두 배가 되어 카드가 글자 중간에서 잘린다.
        assertEquals(
            HomeCard.None,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = true,
                showFirstLockActivationCta = false,
                usageInsightCard = insight,
                hasWebBlockingWarning = true,
            ),
        )
    }

    @Test
    fun `the resume card comes back once the warning is gone`() {
        // 물러난 카드는 사라진 것이 아니다. 권한을 다시 받아 경고가 걷히면 같은 조건에서
        // 그대로 다시 올라와야 한다. 그러지 않으면 약속은 조용히 실패한 채 남는다.
        assertEquals(
            HomeCard.FirstPromiseResume,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = true,
                showFirstLockActivationCta = false,
                usageInsightCard = insight,
                hasWebBlockingWarning = false,
            ),
        )
    }

    @Test
    fun `nothing to show stays empty`() {
        assertEquals(
            HomeCard.None,
            decideHomeCard(
                isKeep = false,
                hasActiveTimedLock = false,
                hasFirstPromiseResumeCard = false,
                showFirstLockActivationCta = false,
                usageInsightCard = UsageInsightCardUiState.Hidden,
                hasWebBlockingWarning = false,
            ),
        )
    }
}
