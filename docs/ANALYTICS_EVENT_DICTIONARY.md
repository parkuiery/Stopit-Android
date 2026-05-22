# 스탑잇 Analytics Event Dictionary

이 문서는 앱 코드의 analytics 계약과 GA4 조회 기준을 한곳에 정리한다.

## 목적

- 이벤트명/파라미터를 코드와 동일하게 유지한다.
- `screen_view` 이름을 안정적으로 관리해 `(not set)` 비중을 낮춘다.
- 퍼널/리뷰/수익화 분석 시 어떤 이벤트를 봐야 하는지 빠르게 확인한다.

## 소스 오브 트루스

- 이벤트/파라미터 상수: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Firebase 구현: `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- 단위 테스트: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`
- 온보딩 screen view 테스트: `app/src/test/java/com/uiery/keep/feature/onboarding/OnboardingAnalyticsViewModelTest.kt`

## screen_view 계약

| 화면 | screen_name | 코드 진입점 |
| --- | --- | --- |
| 홈 | `HomeScreen` | `HomeViewModel` |
| 메뉴 | `MenuScreen` | `MenuViewModel` |
| 히스토리 | `HistoryScreen` | `HistoryViewModel` |
| 루틴 | `RoutineScreen` | `RoutineViewModel` |
| 차단 화면 | `BlockScreen` | `BlockViewModel` |
| 잠금 화면 광고/배너 문맥 | `LockScreen` | `LockScreen`, `TrackedBannerAd` |
| 온보딩 소개 | `OnboardingIntroScreen` | `IntroViewModel.onStepViewed()` |
| 온보딩 접근성 권한 | `OnboardingPermissionScreen` | `PermissionSettingViewModel.onStepViewed()` |
| 온보딩 알림 권한 | `OnboardingNotificationScreen` | `NotificationSettingViewModel.onStepViewed()` |
| 온보딩 앱 선택 | `OnboardingSelectAppScreen` | `SelectAppViewModel.onStepViewed()` |

원칙:

- 화면 진입 시 `logScreenView(screenName)`를 먼저 호출하고, 필요하면 이어서 step/event를 기록한다.
- 새 화면을 추가할 때는 문자열을 임의로 만들지 말고 `KeepAnalytics.kt`에 상수로 추가한다.
- GA4에서 `(not set)` 비율이 높아지면 새 화면/분기에서 `logScreenView` 누락을 먼저 의심한다.

## 이벤트 딕셔너리

### 온보딩/활성화

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `onboarding_step_view` | `step_name` | 온보딩 스텝 노출 |
| `onboarding_step_complete` | `step_name` | 온보딩 스텝 완료 |
| `permission_outcome` | `permission_name`, `outcome`, `step_name?` | 권한 결과 |
| `app_selection_completed` | `selected_app_count`, `is_onboarding` | 차단 앱 선택 완료 |
| `first_lock_configured` | `source`, `selected_app_count?` | 첫 잠금 설정 완료 |
| `first_core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_package`, `routine_id?` | 첫 핵심 행동 완료 |
| `core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_package`, `routine_id?` | 반복 핵심 행동 완료 |

### 차단/세션/긴급해제

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `lock_session_start` | `source`, `is_routine?` | 잠금 세션 시작 |
| `lock_session_end` | `source`, `end_reason`, `is_routine?` | 잠금 세션 종료 |
| `lock_scheduled` | `schedule_type`, `scheduled_duration_minutes` | 타이머/루틴 예약 |
| `keep_mode_toggled` | `is_enabled` | 홈 Keep 토글 |
| `app_block_intercepted` | `block_source`, `blocked_app_package` | 실제 차단 발생 |
| `emergency_unlock_used` | `source`, `unlock_count_remaining?` | 긴급해제 진입 |
| `emergency_unlock_completed` | `reason`, `duration_minutes`, `remaining_unlocks` | 긴급해제 완료 |

### 디바이스 등록/푸시

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `fcm_token_captured` | 없음 | FCM 토큰 확보 |
| `device_registration_attempted` | 없음 | 디바이스 등록 시도 |
| `device_registration_succeeded` | 없음 | 디바이스 등록 성공 |
| `device_registration_failed` | `reason` | 디바이스 등록 실패 |
| `device_registration_skipped` | `reason` | 디바이스 등록 생략 |

### 리뷰 프롬프트

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `review_prompt_eligible` | 없음 | 리뷰 요청 가능 상태 |
| `review_prompt_shown` | 없음 | 리뷰 프롬프트 노출 |
| `review_prompt_skipped` | `reason` | 앱 차원의 스킵 |
| `review_prompt_failed` | `error` | API/플로우 실패 |

## 주요 파라미터 사전

| 파라미터 | 의미 |
| --- | --- |
| `step_name` | 온보딩 단계 이름 (`intro`, `permission`, `notification`, `select_app`) |
| `permission_name` | 권한 종류 (`accessibility`, `notifications`) |
| `outcome` | 권한 결과 (`granted`, `denied`, `settings_opened`) |
| `source` | 이벤트 발생 출처 (`onboarding`, `home`, `home_timer`, `routine` 등) |
| `block_source` | 차단 발생 출처 (`manual_keep`, `timed_lock`, `routine`) |
| `blocked_app_package` | 차단된 앱 패키지명 |
| `selected_app_count` | 선택된 앱 개수 |
| `is_onboarding` | 온보딩 컨텍스트 여부 |
| `is_routine` | 루틴 기반 세션 여부 |
| `end_reason` | 세션 종료 이유 |
| `reason` | 긴급해제/등록 실패/스킵의 이유 |
| `error` | 리뷰 프롬프트 실패 이유 |
| `elapsed_since_first_open_seconds` | 첫 실행 후 경과 초 |
| `routine_id` | 루틴 식별자 |

## 퍼널 기준

### 첫 잠금 활성화 퍼널

1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

### 리뷰 신뢰 퍼널

1. `review_prompt_eligible`
2. `review_prompt_shown`
3. `review_prompt_skipped` 또는 `review_prompt_failed`
4. Play Console rating count / 평균 평점 후속 비교

## 검증 명령

### 로컬 단위 테스트

```bash
cd /Users/uiel/Desktop/git/Keep-Android
./gradlew :app:testDevDebugUnitTest --tests com.uiery.keep.analytics.FirebaseKeepAnalyticsTest
./gradlew :app:testDevDebugUnitTest --tests com.uiery.keep.feature.onboarding.OnboardingAnalyticsViewModelTest
```

### GA4 screen name 확인

```bash
cd /Users/uiel/Desktop/git/Keep-Android
python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH='/Users/.../stopit-ga4-service-account.json'

creds = service_account.Credentials.from_service_account_file(
    CREDENTIAL_PATH,
    scopes=['https://www.googleapis.com/auth/analytics.readonly'],
)
session = AuthorizedSession(creds)
response = session.post(
    f'https://analyticsdata.googleapis.com/v1beta/properties/{PROPERTY_ID}:runReport',
    json={
        'dateRanges': [{'startDate': '14daysAgo', 'endDate': 'yesterday'}],
        'dimensions': [{'name': 'unifiedScreenName'}],
        'metrics': [{'name': 'screenPageViews'}, {'name': 'activeUsers'}],
        'orderBys': [{'metric': {'metricName': 'screenPageViews'}, 'desc': True}],
        'limit': 50,
    },
)
print(response.status_code)
print(response.text)
PY
```

## 운영 메모

- 신규 화면/이벤트 추가 시 이 문서와 테스트를 같이 갱신한다.
- 커스텀 차원 등록 상태가 바뀌면 `docs/METRICS_ANALYSIS.md`의 조회 가이드도 같이 업데이트한다.
- `(not set)` 비율 재측정 기본 창은 배포 후 14일이다.
