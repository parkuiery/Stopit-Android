package com.uiery.keep.domain.pomodoro

import android.app.AlarmManager
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실기기 재부팅 검증에서 **서비스가 살아나지 않는다**는 사실을 발견하고 만든 정책이다.
 *
 * 차단 자체는 저장된 deadline 으로 이어지므로 재부팅 뒤에도 앱은 막힌다. 되살아나지 않던 것은
 * 진행 알림과 구간 갱신이었다.
 */
class PomodoroSessionRestorePolicyTest {

    @Test
    fun restartsAfterBootBecauseTheProcessIsGone() {
        assertTrue(
            PomodoroSessionRestorePolicy.shouldRestartSessionService(Intent.ACTION_BOOT_COMPLETED),
        )
    }

    @Test
    fun restartsAfterPackageReplacedBecauseTheProcessIsGone() {
        assertTrue(
            PomodoroSessionRestorePolicy.shouldRestartSessionService(
                Intent.ACTION_MY_PACKAGE_REPLACED,
            ),
        )
    }

    /**
     * 시각·타임존 변경은 프로세스를 죽이지 않는다. 서비스가 이미 돌고 있으므로 여기서 또
     * 시작을 걸면 불필요한 `onStartCommand` 를 더할 뿐이다.
     */
    @Test
    fun doesNotRestartWhenTheProcessSurvived() {
        listOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        ).forEach { action ->
            assertFalse(action, PomodoroSessionRestorePolicy.shouldRestartSessionService(action))
        }
    }

    @Test
    fun doesNotRestartOnUnknownAction() {
        assertFalse(PomodoroSessionRestorePolicy.shouldRestartSessionService(null))
        assertFalse(PomodoroSessionRestorePolicy.shouldRestartSessionService("com.example.OTHER"))
    }
}
