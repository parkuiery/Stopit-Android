package com.uiery.keep.domain.pomodoro

import android.content.Intent

/**
 * 재부팅·앱 교체 뒤에 세션 서비스를 다시 세울지 판단한다.
 *
 * 세션 자체는 저장된 deadline 만으로 되살아나므로 **차단은 서비스 없이도 이어진다.** 실기기
 * 재부팅 검증에서 그 사실을 확인했다. 하지만 서비스가 죽어 있으면 진행 알림이 사라지고
 * 저장된 구간도 앱을 열기 전까지 갱신되지 않는다. 세션이 앱 밖에서 이어진다는 계약
 * (`docs/POMODORO_FOCUS_MVP.md` "세션 진행 알림 계약")이 깨지는 지점이다.
 */
internal object PomodoroSessionRestorePolicy {

    /**
     * 프로세스가 죽은 경우에만 다시 세운다.
     *
     * 시각/타임존 변경은 프로세스를 죽이지 않으므로 서비스가 이미 돌고 있다. 그때까지 여기서
     * 시작을 걸면 이미 떠 있는 서비스에 불필요한 `onStartCommand` 를 더한다.
     */
    fun shouldRestartSessionService(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
}
