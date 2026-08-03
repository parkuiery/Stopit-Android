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
            ),
        )
    }
}
