# 스탑잇 Analytics Event Dictionary

스탑잇 Android 앱의 Firebase/GA4 계측 계약을 한곳에 모은 문서다. 지표 해석보다 먼저 이 문서와 앱 코드가 일치하는지 확인한다.

## 목적

- 이벤트명/파라미터/사용 위치를 고정한다.
- 활성화 퍼널과 핵심 가치 이벤트를 같은 언어로 해석한다.
- GA4 커스텀 차원 등록 시 어떤 파라미터가 필요한지 빠르게 확인한다.

## 기준 코드

- 이벤트 계약: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Firebase 구현: `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- 계약 테스트: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`

## screen_view 계약

`logScreenView(screenName)`로 Firebase `screen_view`를 보낸다.

현재 명시적으로 추적되는 화면:

| screenName | 발생 위치 | 목적 |
| --- | --- | --- |
| `HomeScreen` | `HomeViewModel` | 메인 홈 진입 추적 |
| `RoutineScreen` | `RoutineViewModel` | 루틴 목록/관리 진입 추적 |
| `MenuScreen` | `MenuViewModel` init | 설정/메뉴 진입 추적 |
| `HistoryScreen` | `HistoryViewModel` init | 사용 기록 요약 진입 추적 |
| `BlockScreen` | `BlockViewModel` init | 실제 차단 오버레이 진입 추적 |

운영 규칙:

- 새 화면을 추가하면 ad metadata의 `screenName`만 넣지 말고 실제 `screen_view`도 같이 추가한다.
- GA4에서 `(not set)` 비율을 볼 때는 위 screenName들이 조회되는지 먼저 확인한다.

## 핵심 퍼널 정의

### 활성화 퍼널

1. `first_open`
2. `onboarding_step_view` / `onboarding_step_complete`
3. `permission_outcome`
4. `app_selection_completed`
5. `first_lock_configured`
6. `first_core_action_completed`
7. `app_block_intercepted`

### 반복 사용 / 건강성

- `lock_session_start`
- `lock_session_end`
- `emergency_unlock_used`
- `emergency_unlock_completed`
- `core_action_completed`

### 운영 / 신뢰

- `device_registration_attempted`
- `device_registration_succeeded`
- `device_registration_failed`
- `device_registration_skipped`
- `review_prompt_eligible`
- `review_prompt_shown`
- `review_prompt_skipped`
- `review_prompt_failed`

## 이벤트 사전

### 온보딩

| event | params | 설명 |
| --- | --- | --- |
| `onboarding_step_view` | `step_name` | 온보딩 단계 노출 |
| `onboarding_step_complete` | `step_name` | 온보딩 단계 완료 |
| `permission_outcome` | `permission_name`, `outcome`, `step_name?` | 알림/접근성 권한 결과 |
| `app_selection_completed` | `selected_app_count`, `is_onboarding` | 차단 앱 선택 완료 |

### 잠금/차단

| event | params | 설명 |
| --- | --- | --- |
| `first_lock_configured` | `source`, `selected_app_count?` | 첫 잠금 설정 완료 |
| `lock_session_start` | `source`, `is_routine?` | 잠금 세션 시작 |
| `lock_session_end` | `source`, `end_reason`, `is_routine?` | 잠금 세션 종료 |
| `lock_scheduled` | `schedule_type`, `scheduled_duration_minutes` | 타이머/카운트다운/루틴 예약 |
| `keep_mode_toggled` | `is_enabled` | 홈의 Keep 스위치 변경 |
| `app_block_intercepted` | `block_source`, `blocked_app_package` | 실제 차단 오버레이 발생 |
| `first_core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_package`, `routine_id?` | 첫 핵심 가치 경험 |
| `core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_package`, `routine_id?` | 반복 핵심 가치 경험 |

### 긴급 해제

| event | params | 설명 |
| --- | --- | --- |
| `emergency_unlock_used` | `source`, `unlock_count_remaining?` | 긴급해제 사용 시작 |
| `emergency_unlock_completed` | `reason`, `duration_minutes`, `remaining_unlocks` | 긴급해제 완료 |

### 디바이스 등록 / 리뷰

| event | params | 설명 |
| --- | --- | --- |
| `fcm_token_captured` | 없음 | FCM 토큰 저장 |
| `device_registration_attempted` | 없음 | 디바이스 등록 시도 |
| `device_registration_succeeded` | 없음 | 디바이스 등록 성공 |
| `device_registration_failed` | `reason` | 디바이스 등록 실패 |
| `device_registration_skipped` | `reason` | 디바이스 등록 생략 |
| `review_prompt_eligible` | 없음 | 리뷰 요청 가능 상태 도달 |
| `review_prompt_shown` | 없음 | 인앱 리뷰 요청 호출 성공 |
| `review_prompt_skipped` | `reason` | 리뷰 요청 생략 |
| `review_prompt_failed` | `error` | 리뷰 요청 호출 실패 |

## 커스텀 차원 후보

GA4에서 조회성을 확보하려면 다음 이벤트 파라미터를 커스텀 차원으로 우선 등록한다.

- `step_name`
- `permission_name`
- `outcome`
- `source`
- `end_reason`
- `selected_app_count`
- `block_source`
- `blocking_mode`
- `blocked_app_package`
- `reason`
- `routine_id`

주의:

- package name은 cardinality가 높을 수 있으므로 전체 등록 전 샘플/용도를 검토한다.
- `selected_app_count` 같은 수치 파라미터는 커스텀 metric 필요 여부를 함께 검토한다.

## 검증 명령

### 계약 테스트

```bash
./gradlew :app:testProdDebugUnitTest --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest'
./gradlew :app:testProdDebugUnitTest --tests 'com.uiery.keep.feature.menu.MenuViewModelTest'
./gradlew :app:testProdDebugUnitTest --tests 'com.uiery.keep.feature.history.HistoryViewModelTest'
./gradlew :app:testProdDebugUnitTest --tests 'com.uiery.keep.BlockViewModelTest'
```

### 전체 관련 JVM 테스트

```bash
./gradlew :app:testProdDebugUnitTest
```

### GA4 점검

- `unifiedScreenName`별 `screenPageViews`
- `eventName`별 `eventCount`, `totalUsers`
- `appVersion` 분리 조회
- `(not set)` 화면 비율 재측정

## 운영 메모

- Play 인앱 리뷰는 사용자가 실제로 별점을 남겼는지 직접 알려주지 않는다. 현재 앱에서 신뢰할 수 있는 신호는 `eligible / shown / skipped / failed`까지다.
- 이벤트 의미가 바뀌면 코드와 이 문서를 같은 PR에서 함께 바꾼다.
- 새로운 퍼널을 논의할 때는 먼저 이 문서에 event/param 계약을 추가한 뒤 구현한다.
