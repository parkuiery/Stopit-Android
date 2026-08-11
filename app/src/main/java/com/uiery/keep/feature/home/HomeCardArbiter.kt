package com.uiery.keep.feature.home

import com.uiery.keep.feature.home.component.UsageInsightCardUiState

/**
 * 홈이 한 번에 노출하는 카드.
 *
 * 카드 조건은 각각 독립된 저장소에서 계산되므로 그대로 두면 여러 장이 동시에 쌓이고, 사용자는
 * 서로 다른 행동을 동시에 요구받는다. 홈은 언제나 "다음에 할 일" 하나만 제시한다.
 */
enum class HomeCard {
    None,
    FirstPromiseResume,
    FirstLockActivation,
    UsageInsight,
}

/**
 * 우선순위는 "지금 어긋난 사실 먼저, 그다음 사용자가 가치를 경험하는 경로" 순서를 따른다.
 *
 * 1. 웹 차단이 서지 못했다는 경고는 *지금 이 순간*의 거짓을 바로잡는다. 사용자는 막히고
 *    있다고 믿는데 실제로는 막히지 않고 있으므로, 늦게 알수록 손해가 쌓인다. 배너가 이미
 *    상단 한 자리를 쓰고 있으니 카드는 어느 것이든 물러난다.
 * 2. 이미 만든 약속이 동작하지 않는 상태(알람 권한 누락·저장 실패)도 제안이 아니라 고장이다.
 *    잠금이 도는 중이라도 알린다. 숨기면 약속은 조용히 실패하고 사용자는 이유를 알 수 없다.
 * 3. 잠금이 진행 중이면 홈은 상태 화면이다. 남은 시간을 확인하거나 끄고 싶은 충동을 느끼는
 *    순간이므로, 새 설정거리를 던져 화면 밖으로 유도하지 않는다.
 * 4. 차단 대상은 골랐지만 아직 한 번도 잠가보지 않았다면 그 경험이 먼저다.
 * 5. 사용 인사이트는 위 경로가 모두 끝난 뒤의 심화 제안이다.
 *
 * 선택되지 않은 카드는 사라지는 것이 아니라 조건이 유지되는 한 다음 방문에서 다시 후보가 된다.
 */
fun decideHomeCard(
    isKeep: Boolean,
    hasActiveTimedLock: Boolean,
    hasFirstPromiseResumeCard: Boolean,
    showFirstLockActivationCta: Boolean,
    usageInsightCard: UsageInsightCardUiState,
    hasWebBlockingWarning: Boolean,
): HomeCard = when {
    // 경고 배너와 카드는 둘 다 상단에 놓인다. 겹쳐 쌓이면 상단 묶음이 두 배가 되어 카드가
    // 글자 중간에서 잘리고, 두 가지 할 일을 동시에 요구받은 사용자는 어느 쪽도 하지 않는다.
    // 재개 카드도 고장이지만 그것은 "아직 시작되지 않은 약속"이라 조건이 유지되는 한 다음
    // 방문에 다시 올라온다. 경고는 그 자리에서 권한을 다시 받으면 곧 사라지므로, 재개 카드가
    // 묻히는 시간은 짧다.
    hasWebBlockingWarning -> HomeCard.None
    hasFirstPromiseResumeCard -> HomeCard.FirstPromiseResume
    isKeep || hasActiveTimedLock -> HomeCard.None
    showFirstLockActivationCta -> HomeCard.FirstLockActivation
    usageInsightCard !is UsageInsightCardUiState.Hidden -> HomeCard.UsageInsight
    else -> HomeCard.None
}
