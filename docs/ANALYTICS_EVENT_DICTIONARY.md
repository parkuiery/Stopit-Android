# 스탑잇 Analytics Event Dictionary

이 문서는 앱 코드의 analytics 계약과 GA4 조회 기준을 한곳에 정리한다.

## 목적

- 이벤트명/파라미터를 코드와 동일하게 유지한다.
- `screen_view` 이름을 안정적으로 관리해 `(not set)` 비중을 낮춘다.
- 퍼널/리뷰/수익화 분석 시 어떤 이벤트를 봐야 하는지 빠르게 확인한다.
- 첫 잠금 활성화 퍼널의 단계 의미와 운영 해석은 `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`와 함께 본다.

## 관련 문서

- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: #13용 GA4 Admin 수동 등록, metadata 증적, 14일 재측정 런북
- `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`: #14용 canonical activation funnel 계약
- `docs/INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md`: #581용 Play Install Referrer / UTM attribution 계약. `install_referrer_attribution_checked` 이벤트, privacy-safe bucket, campaign link 운영 템플릿, GA4 Admin/release/readback 경계를 관리한다.
- `docs/HOME_STATUS_CTA_STRUCTURE.md`: #463용 Home 상태/CTA 구조 계약. 새 이벤트를 요구하지 않고 기존 `first_lock_configured` / `first_core_action_completed` / `app_block_intercepted` 의미를 보존한다.
- `docs/ADMOB_MONETIZATION_RUNBOOK.md`: 광고 이벤트 해석 guardrail과 수익화 운영 기준
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`: #455용 첫 차단 성공 이후 루틴 0개 사용자 대상 루틴 생성 soft CTA 계약
- `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`: #479용 `routines_count` user property coverage 보강 계약. `customUser:routines_count` 조회 가능성과 실제 active user 커버리지를 분리한다.
- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`: #407용 루틴 템플릿 공유 MVP, privacy-safe payload, analytics/QA 계약
- `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`: #465용 LockHistory 성과 리포트 UX, empty/low-data 카피, top apps positive framing, privacy-safe analytics/QA 계약
- `docs/GOAL_LOCK_MVP.md`: #417용 목표 잠금 MVP, 기간 기반 장기 잠금, Home card, analytics/QA 계약
- `docs/PARENT_MODE_MVP.md`: #471용 부모 모드 / 아이에게 폰 주기 same-device MVP, 보호자 PIN, privacy-safe analytics/QA 계약
- `docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md`: #694용 긴급해제 설정 변경 analytics 계약. 설정 ON/OFF, daily limit, duration option, reason required, refill mode, manual reset을 enum/bucket만으로 계측하고 GA4/Admin/readback 경계를 분리한다.

## 소스 오브 트루스

- 이벤트/파라미터 상수: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Firebase 구현: `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- AdMob 배너 계측 래퍼: `app/src/main/java/com/uiery/keep/analytics/TrackedBannerAd.kt`
- 리뷰 eligibility/launch 구현: `app/src/main/java/com/uiery/keep/feature/review/ReviewEligibilityEvaluator.kt`, `app/src/main/java/com/uiery/keep/feature/review/InAppReviewManager.kt`
- 리뷰 drain 지점: `app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt`, `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt`
- 집중 요약 공유 구현: `app/src/main/java/com/uiery/keep/feature/lockhistory/LockHistoryViewModel.kt`, `app/src/main/java/com/uiery/keep/feature/lockhistory/FocusSummarySharePayload.kt`
- 잠금 기록 성과 리포트 구현: `app/src/main/java/com/uiery/keep/feature/lockhistory/LockHistoryScreen.kt`, `app/src/main/java/com/uiery/keep/feature/lockhistory/LockHistoryPerformanceReportReadModel.kt`, `app/src/main/java/com/uiery/keep/feature/lockhistory/LockHistoryViewModel.kt` (PR #485로 UI/read model develop 반영; 2026-06-05 code-lane instrumentation으로 `lock_history_*` event 코드 계약 추가)
- 루틴 count user property: `app/src/main/java/com/uiery/keep/analytics/RoutineCountAnalyticsSync.kt` (`KeepAnalyticsUserProperty.ROUTINES_COUNT`, `KeepAnalytics.setRoutinesCount(...)`)
- 루틴 템플릿 공유 구현 후보: `app/src/main/java/com/uiery/keep/feature/routine/RoutineViewModel.kt`, `RoutineTemplateSharePayload` helper(구현 시 추가)
- 루틴 생성 CTA 구현 후보: `HomeViewModel` / `LockHistoryViewModel` / `RoutineViewModel` navigation contract(구현 시 추가)
- 반복 차단 루틴 추천 구현 foothold: `app/src/main/java/com/uiery/keep/feature/routine/RepeatBlockRoutineSuggestionPolicy.kt`, `app/src/main/java/com/uiery/keep/feature/routine/RepeatBlockRoutineSuggestionStore.kt`, `app/src/main/java/com/uiery/keep/feature/routine/RoutineNavigation.kt`, `app/src/main/java/com/uiery/keep/feature/routine/RoutineBottomSheetViewModel.kt`, `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`, `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt` (2026-06-06 code/QA lane에서 policy + analytics event contract, RoutineRoute prefill 적용, dismiss local store 추가; 2026-06-08 #651 code-lane에서 analytics boundary는 `RepeatBlockRoutineSuggestionAnalyticsPayload` DTO로 분리해 feature-local suggestion object를 analytics API/Firebase adapter가 직접 import하지 않는다. Home/LockHistory CTA UI wiring/release/GA4 등록 전까지 live event 0건은 수요 없음으로 해석하지 않는다.)
- 목표 잠금 구현 후보: `GoalLockPolicy` / 목표 잠금 model·repository·Home card ViewModel(구현 시 추가)
- 부모 모드 코드 계약: `app/src/main/java/com/uiery/keep/feature/parentmode/ParentModePolicy.kt`, `app/src/main/java/com/uiery/keep/feature/parentmode/ParentModeSessionStore.kt`, `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`, `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt` (PR #519로 순수 정책 + `parent_mode_*` analytics API, PR #584로 session persistence + `block_source=parent_mode` Accessibility decision foothold 추가)
- 긴급해제 설정 변경 코드 계약: `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsViewModel.kt`, `app/src/main/java/com/uiery/keep/datastore/EmergencyUnlockSettingsStore.kt`, `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`, `FirebaseKeepAnalytics.kt` (PR #698 / merge commit `8c303d75204bf9b2b6ab1e0ed4c9b6d8e2489260`로 `emergency_unlock_settings_changed` / `emergency_unlock_manual_reset_requested` Android wiring과 privacy-safe JVM payload 보장이 `develop`에 반영됨. GA4 Admin 등록, release/tag/Play deploy, D+14/D+30 readback 전까지 live event 0건은 adoption 부재로 해석하지 않는다.)
- 단위 테스트: `app/src/test/java/com/uiery/keep/analytics/FirebaseKeepAnalyticsTest.kt`
- 집중 요약 공유 테스트: `app/src/test/java/com/uiery/keep/feature/lockhistory/FocusSummarySharePayloadTest.kt`, `app/src/test/java/com/uiery/keep/feature/lockhistory/LockHistoryViewModelShareTest.kt`
- 광고 계측 테스트: `app/src/test/java/com/uiery/keep/analytics/TrackedBannerAdTest.kt`
- 화면 screen_view 및 차단 화면 첫 가치 피드백 테스트: `app/src/test/java/com/uiery/keep/feature/splash/SplashViewModelAnalyticsTest.kt`, `app/src/test/java/com/uiery/keep/feature/menu/MenuViewModelTest.kt`, `app/src/test/java/com/uiery/keep/feature/lockhistory/LockHistoryViewModelShareTest.kt`, `app/src/test/java/com/uiery/keep/feature/lockhistory/blockedapps/BlockedAppsViewModelAnalyticsTest.kt`, `app/src/test/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsViewModelAnalyticsTest.kt`, `app/src/test/java/com/uiery/keep/feature/devtool/DevToolViewModelAnalyticsTest.kt`, `app/src/test/java/com/uiery/keep/KeepAppNavigationPolicyTest.kt`, `app/src/test/java/com/uiery/keep/BlockViewModelTest.kt`, `app/src/test/java/com/uiery/keep/feature/lock/LockViewModelTest.kt`
- 리뷰 관련 테스트: `app/src/test/java/com/uiery/keep/feature/review/ReviewEligibilityEvaluatorTest.kt`, `app/src/test/java/com/uiery/keep/feature/review/InAppReviewManagerTest.kt`, `app/src/test/java/com/uiery/keep/feature/home/HomeViewModelReviewTest.kt`

## screen_view 계약

| 화면 | screen_name | 코드 진입점 |
| --- | --- | --- |
| 스플래시 | `SplashScreen` | `SplashViewModel` |
| 홈 | `HomeScreen` | `HomeViewModel` |
| 메뉴 | `MenuScreen` | `MenuViewModel` |
| 잠금 히스토리(히스토리 도메인의 canonical surface) | `LockHistoryScreen` | `LockHistoryViewModel` |
| 차단 앱 상세 | `BlockedAppsScreen` | `BlockedAppsViewModel` |
| 루틴 | `RoutineScreen` | `RoutineViewModel` |
| 긴급해제 설정 | `EmergencyUnlockSettingsScreen` | `EmergencyUnlockSettingsViewModel` |
| 개발자 도구(dev 전용) | `DevToolScreen` | `DevToolViewModel` |
| 차단 화면 | `BlockScreen` | `BlockViewModel` |
| 잠금 화면 | `LockScreen` | `LockViewModel`, `TrackedBannerAd` |
| 온보딩 소개 | `OnboardingIntroScreen` | `IntroViewModel.onStepViewed()` |
| 온보딩 접근성 권한 | `OnboardingPermissionScreen` | `PermissionSettingViewModel.onStepViewed()` |
| 온보딩 알림 권한 | `OnboardingNotificationScreen` | `NotificationSettingViewModel.onStepViewed()` |
| 온보딩 앱 선택 | `OnboardingSelectAppScreen` | `SelectAppViewModel.onStepViewed()` |

원칙:

- 화면 진입 시 `logScreenView(screenName)`를 먼저 호출하고, 필요하면 이어서 step/event를 기록한다.
- 새 화면을 추가할 때는 문자열을 임의로 만들지 말고 `KeepAnalytics.kt`에 상수로 추가한다.
- GA4에서 `(not set)` 비율이 높아지면 새 화면/분기에서 `logScreenView` 누락을 먼저 의심한다.

## 이벤트 딕셔너리

### 획득 / Install Referrer attribution

Play Install Referrer / UTM attribution의 제품·ops 계약은 `docs/INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md`(#581)를 source of truth로 본다. PR #586으로 parser, campaign link helper, analytics event/parameter constants와 privacy-safe unit regression foothold가 `develop`에 반영됐고, PR #590(`ae26293a`)으로 `InstallReferrerClient` provider + first-launch one-shot reporter wiring까지 추가됐다. 다만 GA4 Admin 등록, release/tag/Play deploy, 14일/30일 readback 전에는 GA4 `Direct` 감소나 ASO 회복을 주장하지 않는다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `install_referrer_attribution_checked` | `referrer_status`, `utm_source_type`, `utm_medium_type`, `campaign_bucket`, `link_surface?`, `lookup_latency_bucket?` | 첫 실행/설치 attribution lookup이 terminal status에 도달했을 때 1회 기록하는 후보 이벤트. referrer/UTM coverage를 privacy-safe bucket으로 확인한다. |

현재 bucket 계약 후보:

- `referrer_status`: `success`, `missing`, `unavailable`, `timeout`, `error`, `malformed`
- `utm_source_type`: `play_store`, `discord`, `web`, `qr`, `paid_search`, `community`, `unknown`, `none`
- `utm_medium_type`: `organic`, `social`, `referral`, `paid`, `qr`, `owned`, `unknown`, `none`
- `campaign_bucket`: `aso_baseline`, `launch`, `review_push`, `routine_share`, `manual_test`, `other`, `none`
- `link_surface`: `play_store_listing`, `discord`, `website`, `docs`, `qr`, `ad`, `unknown`, `none`
- `lookup_latency_bucket`: `0_499ms`, `500_999ms`, `1000_1999ms`, `2000ms_plus`, `not_measured`

금지 payload/query 축: raw referrer URL, 검색어/search term, email/phone/account id/Discord user 또는 channel name, 개인·소규모 배포 대상을 드러내는 raw campaign name, raw timestamp, raw URL path, arbitrary query key-value.

### 온보딩/활성화

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `onboarding_step_view` | `step_name` | 온보딩 스텝 노출 |
| `onboarding_step_complete` | `step_name` | 온보딩 스텝 완료 |
| `permission_outcome` | `permission_name`, `outcome`, `step_name?` | 권한 결과 |

알림 권한 온보딩 계약:
- 현재 지원 범위는 minSdk 33 / Android 13+ `POST_NOTIFICATIONS` runtime permission이다. 따라서 현재 릴리즈/QA에서 검증해야 하는 canonical 흐름은 runtime dialog 허용/거절과 notification-denied fallback이다.
- Android 13+ runtime permission dialog에서는 사용자가 `POST_NOTIFICATIONS`를 거절해도 온보딩을 막지 않는다. 이 경우 `permission_outcome(permission_name=notifications, outcome=denied)`와 `onboarding_step_complete(step_name=notification)`를 함께 남긴 뒤 앱 선택 단계로 진행한다.
- notification-denied 사용자의 루틴 시작 안내는 `POST_NOTIFICATION ignore` receiver fallback baseline으로 별도 검증한다.
- Historical / out of scope: `permission_outcome(permission_name=notifications, outcome=settings_opened)`와 Android 12L 이하 legacy 설정 왕복은 minSdk를 다시 낮출 때만 복원 검토한다. minSdk 33 유지 상태에서는 현재 검증 대상이 아니다.

| `app_selection_completed` | `selected_app_count`, `is_onboarding` | 차단 앱 1개 이상 선택 완료 (`selected_app_count >= 1`) |
| `first_lock_configured` | `source`, `selected_app_count?` | 첫 잠금 설정 완료. 온보딩/홈 Keep 토글/홈 타이머 모두 앱 1개 이상 선택 이후에만 기록 |
| `first_core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_category_bucket`, `routine_id?`, `goal_lock_id?` | 첫 핵심 행동 완료. `blocked_app_package` 원문은 #611 privacy 계약에 따라 퇴역 대상 |
| `core_action_completed` | `elapsed_since_first_open_seconds`, `blocking_mode`, `blocked_app_category_bucket`, `routine_id?`, `goal_lock_id?` | 반복 핵심 행동 완료. `blocked_app_package` 원문은 #611 privacy 계약에 따라 퇴역 대상 |

첫 가치 경험 해석:
- `first_lock_configured`는 차단 준비 완료 신호이며 실제 차단 완료가 아니다. 홈 CTA/타이머 안내 문구가 이 이벤트 직후에 “차단 완료”라고 과장하면 안 된다.
- 홈 Keep 시작/타이머 예약 안내 snackbar는 `first_lock_configured`가 최초 기록될 때만 1회 노출한다. 이미 첫 잠금을 기록한 사용자는 `first_core_action_completed` / `app_block_intercepted` 흐름으로 해석하고 준비 안내를 반복하지 않는다.
- 현재 block 화면 진입 경로는 `BlockViewModel.trackBlockShown(...)`에서 `app_block_intercepted`를 먼저 기록한 뒤, 최초 1회만 `first_core_action_completed`를 기록한다. #14 후속 피드백/문구/테스트는 이 순서를 유지해야 한다.
- 루틴 차단의 `routine_id`는 Activity extra 경계에서 문자열로 정규화해 analytics payload까지 전달한다. `block_source=routine`일 때만 non-null이어야 하며, 수동 Keep/타이머/목표 잠금 차단에서는 null/미전송 상태를 유지한다.
- 목표 잠금 차단의 `goal_lock_id`는 AccessibilityService block decision → BlockActivity extra → BlockViewModel analytics payload 경계에서 문자열로 정규화해 전달한다. `block_source=goal_lock`일 때만 non-null이어야 하며, 수동 Keep/타이머/루틴 차단에서는 null/미전송 상태를 유지한다.
- 차단 화면의 첫 성공 피드백은 `HAS_TRACKED_FIRST_CORE_ACTION=false`인 최초 차단 진입에서만 노출한다. 반복 차단은 `core_action_completed`만 기록하고 같은 축하/성공 피드백을 반복하지 않는다.
- 첫 성공 피드백을 추가하더라도 차단 앱 이름/package 같은 민감 정보는 불필요하게 노출하지 않는다.
- 차단 앱 analytics privacy 계약은 `docs/BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md`(#611)를 source of truth로 본다. PR #617(`f8eb0ebe`) 이후 `blocked_app_package` 원문은 GA4 payload/custom dimension 등록 대상에서 퇴역했고, `blocked_app_category_bucket` 같은 privacy-safe bucket만 activation/runtime 세부 해석에 사용한다.
- 홈 화면 상태/CTA 구조 개선(#463)의 source of truth는 `docs/HOME_STATUS_CTA_STRUCTURE.md`다. Home copy/CTA를 바꾸더라도 `first_lock_configured`는 준비 완료, `first_core_action_completed`는 첫 가치 경험, `app_block_intercepted`는 실제 차단이라는 의미를 유지한다. #463은 새 이벤트를 필수 요구하지 않으며, 새 Home CTA 실험 이벤트를 추가한다면 privacy-safe enum/bucket만 허용한다.
- 차단 화면 카피/액션 위계 개선(#464)의 source of truth는 `docs/BLOCK_SCREEN_COPY_HIERARCHY.md`다. 이 계약은 새 이벤트를 요구하지 않는다. `BlockScreen` copy/CTA/emergency unlock 상태를 바꾸더라도 기존 `app_block_intercepted` → 최초 1회 `first_core_action_completed` → 반복 `core_action_completed` 순서와 `emergency_unlock_used` / `emergency_unlock_completed` 의미를 유지한다.
- #464에서 별도 copy 실험 이벤트를 추가한다면 privacy-safe enum/bucket만 허용하고, 앱 이름/package/raw history/raw timestamp를 payload나 query 축으로 쓰지 않는다.

### 차단/세션/긴급해제

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `lock_session_start` | `source`, `is_routine?` | 잠금 세션 시작 |
| `lock_session_end` | `source`, `end_reason`, `is_routine?` | 잠금 세션 종료 |
| `lock_scheduled` | `schedule_type`, `scheduled_duration_minutes` | 타이머/루틴 예약 |
| `keep_mode_toggled` | `is_enabled` | 홈 Keep 토글 |
| `app_block_intercepted` | `block_source`, `blocked_app_category_bucket`, `routine_id?`, `goal_lock_id?` | 실제 차단 발생. `blocked_app_package` 원문은 #611 privacy 계약에 따라 퇴역 대상 |
| `emergency_unlock_used` | `source`, `unlock_count_remaining?` | 긴급해제 진입 |
| `emergency_unlock_completed` | `reason`, `duration_minutes`, `remaining_unlocks` | 긴급해제 완료 |

긴급해제 flow copy/step 개선(#467)의 source of truth는 `docs/EMERGENCY_UNLOCK_FLOW_COPY.md`다. 이 계약은 새 이벤트를 요구하지 않는다. PR #575(`1a7c677`)의 Compose UI baseline이 reason-required ON/OFF flow를 자동 검증하고 PR #593(`79fdee8`)의 countdown TalkBack baseline이 waiting copy/remaining seconds/cancel affordance 접근성 노출을 고정하며 PR #604(`3e97f548`)의 selected reason reflection helper baseline이 선택 사유 확인 문구를 보강하더라도, Reason/app/duration/countdown copy 변경은 `emergency_unlock_completed.reason` existing enum key(`work`, `contact`, `info`, `habit`, `boredom`, `other`) 의미를 유지해야 하며 display label이나 custom reason 원문으로 대체하지 않는다. Reason-required-off 사용자는 reason 분포 해석에서 별도 confidence guardrail로 분리한다.

#467에서 별도 flow 실험 이벤트를 추가한다면 privacy-safe enum/bucket만 허용한다. 금지 payload/query 축: custom reason 원문, 앱 이름, package, raw selected app list, raw history, raw timestamp.

### 디바이스 등록/푸시

현재 앱의 production 책임은 **FCM token 로컬 저장**이다. 백엔드 device registration 파이프라인은 제거되어 있으며, `DeviceTokenManager.saveDeviceToken(...)`은 토큰 저장 후 registration 성공/실패가 아니라 skip reason으로 현재 상태를 남긴다.

| 이벤트명 | 주요 파라미터 | 현재 발생 여부 | 설명 |
| --- | --- | --- | --- |
| `fcm_token_captured` | 없음 | 발생 | FCM 토큰을 로컬 DataStore에 저장한 시점 |
| `device_registration_attempted` | 없음 | 발생 | legacy registration 흐름과의 호환/관측용 시도 이벤트. 현재는 외부 backend 호출을 의미하지 않는다. |
| `device_registration_skipped` | `reason` | 발생 | backend 제거 또는 빈 토큰 때문에 registration이 생략된 상태. 현재 reason 값은 `backend_removed`, `missing_fcm_token`이다. |
| `device_registration_succeeded` | 없음 | 제거됨 | 현재 코드 API/event constant에서 제거됐다. backend registration 재도입 전에는 성공 이벤트로 해석하지 않는다. |
| `device_registration_failed` | `reason` | 제거됨 | 현재 코드 API/event constant에서 제거됐다. backend registration 재도입 전에는 실패율 지표로 해석하지 않는다. |

운영 원칙:

- `fcm_token_captured`와 `device_registration_skipped(reason=backend_removed)`는 “토큰 저장은 됐지만 backend device registration은 제거되어 호출하지 않았다”는 계약으로 함께 해석한다.
- `device_registration_succeeded` / `device_registration_failed`가 GA4에 새로 보이면 먼저 과거 앱 버전, manual/test event, 또는 코드 call site 재도입 여부를 확인한다. 현재 dictionary 기준에서는 살아 있는 제품 지표가 아니라 제거된 legacy 이벤트다.
- 백업/복원 또는 새 기기 QA에서 확인할 것은 backend registration 성공이 아니라 `KeepMessagingServiceIntegrationTest` 기준의 stale FCM token overwrite / local persistence wiring이다.

### 리뷰 프롬프트

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `review_prompt_eligible` | 없음 | 리뷰 요청이 arm 되어 다음 홈 루트에서 노출 시도를 할 수 있는 상태 |
| `review_prompt_shown` | 없음 | Play review sheet launch 성공 |
| `review_prompt_skipped` | `reason` | eligibility 실패 또는 홈 drain 단계의 노출 보류/중단 |
| `review_prompt_failed` | `error` | API/launcher 실패 |

세부 arm/drain 규칙과 `REVIEW_PENDING` / cooldown 상태 계약은 `docs/REVIEW_PROMPT_LIFECYCLE.md`를 source of truth로 본다.
현재 홈 drain 단계에서는 `NotHomeRoot`, `NoActivity`, live eligibility reason이 `review_prompt_skipped.reason`의 대표값이다. `NotHomeRoot`와 `NoActivity`는 일시적 노출 보류이므로 `REVIEW_PENDING`을 유지하고, `AccessibilityOff`/`QuietHours`/`KillSwitch` 같은 live eligibility 실패는 pending을 삭제하는 최종 skip으로 해석한다. `NoActivity`는 Home `LocalContext.current`의 `ContextWrapper` 체인에서도 Activity를 찾지 못한 경우에만 기록되어야 하므로, wrapper가 Activity를 감싼 정상 Compose 경로를 `NoActivity`로 해석하지 않는다. `review_prompt_failed`도 Play review launch/API 실패이므로 cooldown을 기록하지 않고 `REVIEW_PENDING`을 유지해 다음 홈 루트 진입에서 재시도한다.

### 집중 요약 공유

`LockHistory` 주간 요약 공유 MVP의 제품/QA 계약은 `docs/FOCUS_SUMMARY_SHARE_MVP.md`를 source of truth로 본다. 공유문과 이벤트 파라미터에는 앱 이름, package name, raw session 목록, raw duration을 넣지 않고 bucket/기간 타입만 남긴다. #597은 공유 payload의 locale resource/template debt이며 analytics schema 변경 이슈가 아니다. `focus_summary_share_*` 이벤트명과 `period_type`, `session_count_bucket`, `duration_minutes_bucket`, `reason` bucket 계약은 유지한다. 공유 본문 locale, raw rendered text, raw duration string, app name/package/topApps/raw session은 GA4 payload나 custom dimension registration 대상이 아니다.

### 잠금 기록 성과 리포트

`LockHistory` 성과 리포트 UX의 제품/QA 계약은 `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`를 source of truth로 본다. #465는 #211 공유 CTA와 같은 화면을 쓰지만, 1차 목표는 외부 공유가 아니라 개인 성과 해석과 재방문 동기 강화다. PR #485로 `LockHistoryPerformanceReportReadModel`과 summary/top apps UI copy는 `develop`에 반영됐고, 2026-06-05 code-lane instrumentation으로 아래 `lock_history_*` 이벤트가 코드에 추가됐다. PR #566은 summary/top apps 성과 copy가 TalkBack content description으로 합쳐져 전달되는 focused accessibility baseline을 추가했고, PR #579는 Top Apps card의 rank/app label/block count/duration까지 같은 content description에 포함되도록 회귀 범위를 확장했다. empty/low-data 상태도 실패처럼 보이지 않게 만들고, top apps는 `위험 앱 목록`보다 `막아낸 성과`로 읽히게 한다. analytics payload에는 앱 이름/package/raw session/raw timestamp/raw duration을 보내지 않고 enum/bucket만 남긴다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `lock_history_performance_summary_viewed` | `period_type`, `report_state`, `session_count_bucket`, `duration_minutes_bucket` | LockHistory summary card가 성과 리포트 read model로 표시됨 |
| `lock_history_top_apps_viewed` | `period_type`, `top_apps_count_bucket` | top apps 성과 섹션 표시 |

현재 bucket 계약:

- `period_type`: `week`, `month`
- `report_state`: `empty`, `low_data`, `has_history`
- `session_count_bucket`: `0`, `1`, `2_3`, `4_6`, `7_plus`
- `duration_minutes_bucket`: `0`, `1_29`, `30_59`, `60_119`, `120_239`, `240_plus`
- `top_apps_count_bucket`: `0`, `1`, `2_3`, `4_plus`
- 2026-06-05 code-lane instrumentation 이후에도 release/tag/Play deploy, GA4 Admin 등록, metadata 확인 전에는 live event 0건을 수요 없음이나 UX 실패로 해석하지 않는다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `focus_summary_share_tapped` | `period_type`, `session_count_bucket`, `duration_minutes_bucket` | 주간 LockHistory 공유 CTA 탭 |
| `focus_summary_share_sheet_opened` | `period_type`, `session_count_bucket`, `duration_minutes_bucket` | Android share sheet launch 시도/성공 |
| `focus_summary_share_failed` | `period_type`, `reason` | share sheet를 열 수 없어 공유를 중단 |

현재 bucket 계약:

- `period_type`: `week`만 기록한다. 월간 화면에서는 공유 payload/CTA를 만들지 않는다.
- `session_count_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `duration_minutes_bucket`: `1_29`, `30_59`, `60_119`, `120_239`, `240_plus`
- `focus_summary_share_failed.reason`: `activity_not_found`

### 루틴 템플릿 공유

루틴 템플릿 공유 MVP의 제품/QA 계약은 `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`를 source of truth로 본다. MVP는 Android share sheet 기반 텍스트 공유이며, deep link/import는 별도 결정 게이트 전까지 구현-ready로 보지 않는다. 공유문과 이벤트 파라미터에는 앱 이름/package/lockApplications/raw session history 금지 원칙을 적용하고 enum/bucket/boolean만 남긴다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `routine_template_share_tapped` | `template_category`, `repeat_days_bucket`, `time_window_bucket`, `routine_name_included` | 루틴 템플릿 공유 CTA 탭 |
| `routine_template_share_sheet_opened` | `template_category`, `repeat_days_bucket`, `time_window_bucket`, `routine_name_included` | Android share sheet launch 시도/성공 |
| `routine_template_share_failed` | `template_category`, `reason` | share sheet 또는 payload 생성 실패 |

현재 bucket 계약:

- `template_category`: `study`, `work`, `night_focus`, `custom`
- `repeat_days_bucket`: `weekday`, `weekend`, `daily`, `custom_days`, `none`
- `time_window_bucket`: `morning`, `afternoon`, `evening`, `night`, `overnight`, `custom_window`
- `routine_name_included`: `true` / `false`; 이름 원문은 analytics에 넣지 않는다.
- `routine_template_share_failed.reason`: `activity_not_found`, `invalid_template`

### 목표 잠금

목표 잠금 MVP의 제품/QA 계약은 `docs/GOAL_LOCK_MVP.md`를 source of truth로 본다. MVP는 기간 기반 장기 잠금을 `all_day`와 `scheduled` 두 방식으로 지원하고, Home card/section에 진행 상태를 보여준다. 목표 이름 원문/app package/app label 금지 원칙을 적용하고 enum/bucket만 analytics에 남긴다. 2026-06-07 기준 `goal_lock_created`, `goal_lock_ended_early`, `goal_lock_completed`, goal-lock source의 `goal_lock_id` attribution 코드 계약은 `develop`에 반영됐으며, 생성/상세/Home 완료 경로는 유효한 상태 전환에서만 bucket-only payload를 기록하도록 고정한다. 생성 화면 compact-height CTA 접근성도 Compose instrumentation baseline으로 들어왔지만, GA4 Admin 등록, release/tag/Play deploy, live metadata/readback 전에는 이벤트 0건이나 breakdown 부재를 제품 실패로 해석하지 않는다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `goal_lock_create_started` | `entry_surface` | 목표 잠금 생성 플로우 진입 |
| `goal_lock_created` | `duration_selection_type`, `lock_mode`, `selected_app_count_bucket`, `goal_name_type` | 유효한 목표 잠금 저장 완료 |
| `goal_lock_completed` | `lock_mode`, `duration_days_bucket` | 종료일 경과 후 자동 완료 처리 |
| `goal_lock_ended_early` | `lock_mode`, `elapsed_days_bucket`, `reason` | 사용자가 기간 전 종료 |
| `goal_lock_updated` | `lock_mode`, `changed_field` | 기간/앱/시간대/이름/잠금 방식 수정 저장 |

현재 bucket 계약:

- `entry_surface`: `home`, `routine`, `menu`, `goal_lock_detail`
- `duration_selection_type`: `preset_days`, `custom_days`, `end_date`
- `lock_mode`: `all_day`, `scheduled`
- `selected_app_count_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `goal_name_type`: `preset_exam`, `preset_sns`, `preset_game`, `preset_sleep`, `custom`; 목표 이름 원문은 analytics에 넣지 않는다. Preset CTA는 표시 문자열/locale literal 비교가 아니라 locale-independent preset key로 분류하고, 사용자가 같은 문구를 직접 입력한 경우는 `custom`으로 기록한다.
- `duration_days_bucket`: `1_6`, `7`, `8_14`, `15_30`, `31_plus`
- `elapsed_days_bucket`: `0`, `1_2`, `3_6`, `7_14`, `15_plus`
- `goal_lock_ended_early.reason`: `user_confirmed`, `validation_reset`, `unknown`
- `goal_lock_updated.changed_field`: `duration`, `apps`, `schedule`, `name`, `lock_mode`

### 루틴 생성 CTA

첫 차단 성공 이후 루틴 생성 CTA 실험의 제품/QA 계약은 `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`를 source of truth로 본다. 이 CTA는 `first_core_action_completed` 또는 `app_block_intercepted` 이후의 루틴 0개 사용자에게만 노출되는 soft CTA이며, onboarding / pre-first-lock 사용자는 제외한다. Routine empty state, 광고 배너, #407 루틴 템플릿 공유 CTA와 같은 slot에서 압박하지 않는다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `routine_creation_cta_shown` | `surface`, `activation_stage`, `has_routine`, `cta_variant` | 루틴 생성 CTA 노출 |
| `routine_creation_cta_clicked` | `surface`, `activation_stage`, `has_routine`, `cta_variant` | CTA 클릭 후 루틴 생성 흐름 이동 시도 |
| `routine_creation_cta_dismissed` | `surface`, `activation_stage`, `has_routine`, `cta_variant` | 명시 닫기/나중에 보기 등 사용자의 비전환 |

현재 enum 계약:

- `surface`: `home`, `lock_history`, `post_block_success`
- `activation_stage`: `post_first_core_action`, `returning_blocked_user`
- `has_routine`: MVP에서는 `false`만 허용한다. 루틴 보유자에게 보이면 QA 실패다.
- `cta_variant`: `default`부터 시작한다. copy/placement 실험 전에는 다중 variant로 해석하지 않는다.
- 앱 이름, package name, `lockApplications`, raw session history, raw lock timestamp, `routine_id`는 CTA shown/clicked/dismissed payload에 넣지 않는다.

### 반복 차단 기반 자동 루틴 제안

반복 차단 패턴 기반 자동 루틴 제안의 제품/QA 계약은 `docs/REPEAT_BLOCK_ROUTINE_SUGGESTION.md`를 source of truth로 본다. MVP는 기존 LockHistory/차단 기록에서 반복되는 시간대·요일·앱 카테고리 신호를 로컬에서 계산하고, 기존 활성 루틴과 겹치지 않는 후보 1개만 루틴 생성 prefill로 제안한다. 2026-06-06 code/QA lane에서 `RepeatBlockRoutineSuggestionPolicy`, `repeat_block_routine_suggestion_*` analytics method/constant, `RoutineRoute`/`RoutineBottomSheetViewModel` prefill 적용, `RepeatBlockRoutineSuggestionStore` dismiss persistence 계약이 추가됐다. 아직 Home/LockHistory/성과 리포트 CTA UI wiring, dismiss/apply store UI wiring, release/tag/Play deploy, GA4 Admin 등록 전에는 live event 0건을 수요 없음이나 UX 실패로 해석하지 않는다. onboarding / pre-first-lock 사용자는 제외하고, 비난형 copy와 앱 이름/package/raw history/raw timestamp payload 및 dismiss store 저장을 금지한다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `repeat_block_routine_suggestion_shown` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 반복 차단 기반 루틴 추천 노출 |
| `repeat_block_routine_suggestion_clicked` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 추천 CTA 클릭 후 루틴 생성 prefill 흐름 진입 |
| `repeat_block_routine_suggestion_dismissed` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 추천 닫기/나중에 보기 |
| `repeat_block_routine_suggestion_applied` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 추천 prefill에서 루틴 저장 완료 |

현재 enum/bucket 계약:

- `surface`: `home`, `post_block_success`, `lock_history`, `performance_report`
- `suggestion_reason`: `repeat_block_time_bucket`, `repeat_block_day_time`, `rapid_retry`
- `time_bucket`: `morning`, `afternoon`, `evening`, `night`, `overnight`
- `day_type`: `weekday`, `weekend`, `daily`, `custom_days`
- `category_bucket`: `social`, `video`, `game`, `shopping`, `browser`, `unknown`
- `repeat_count_bucket`: `3_5`, `6_10`, `10_plus`
- `routine_coverage_state`: `not_covered`, `partially_covered`; `covered`는 추천 노출 실패로 간주한다.
- `suggestion_variant`: `default`
- 앱 이름, package name, `lockApplications`, raw session history, raw timestamp, raw retry count, raw routine name, `routine_id`는 추천 shown/clicked/dismissed payload에 넣지 않는다.

### 부모 모드

부모 모드 / 아이에게 폰 주기 MVP의 제품/QA 계약은 `docs/PARENT_MODE_MVP.md`를 source of truth로 본다. MVP는 부모가 자신의 휴대폰을 아이에게 잠깐 넘기는 same-device flow이며, 보호자 PIN으로 시작/연장/종료를 확인한다. PR #519 이후 `parent_mode_*` analytics API와 privacy-safe bucket contract가 코드에 들어갔고, PR #584 이후 active session의 foreground 차단은 `app_block_intercepted.block_source=parent_mode`로 구분된다. 아직 setup/active UI, PIN runtime flow, release/tag/Play deploy, GA4 Admin metadata/readback 전이므로 live product conclusion은 낮은 confidence로 둔다. 아이 이름/앱 이름/package/raw session history 금지 원칙을 적용하고 enum/bucket만 analytics에 남긴다. 원격 자녀 기기 관리, 가족 계정, 서버 동기화는 별도 후속 gate 전까지 구현-ready로 보지 않는다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `parent_mode_duration_selected` | `duration_minutes_bucket` | 부모 모드 setup에서 사용 시간 선택 |
| `parent_mode_allowed_apps_selected` | `allowed_app_count_bucket` | 허용 앱 1개 이상 선택 완료 |
| `parent_mode_started` | `duration_minutes_bucket`, `allowed_app_count_bucket` | 보호자 PIN 확인 후 부모 모드 시작 |
| `parent_mode_completed` | `duration_minutes_bucket`, `end_reason` | 시간 만료 또는 정상 종료로 session 완료 |
| `parent_mode_unlocked_by_pin` | `pin_result`, `end_reason` | 보호자 PIN으로 해제/연장 흐름 통과 |
| `parent_mode_extended` | `extension_minutes_bucket` | 보호자 PIN 확인 후 시간 연장 |
| `parent_mode_block_intercepted` | `block_context` | 부모 모드 중 허용되지 않은 앱/우회 surface 차단 |
| `parent_mode_cancelled` | `end_reason` | 시작 전 또는 active 중 취소 |

현재 enum/bucket 계약:

- `duration_minutes_bucket`: `1_9`, `10`, `11_20`, `21_30`, `31_60`, `61_plus`
- `extension_minutes_bucket`: `1_9`, `10`, `11_20`, `21_30`, `31_plus`
- `allowed_app_count_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `pin_result`: `success`, `failure`, `not_configured`
- `end_reason`: `time_expired`, `pin_unlocked`, `cancelled_before_start`, `cancelled_by_parent`, `system_interrupted`, `unknown`
- `block_context`: `disallowed_app`, `settings_surface`, `recent_apps`, `notification_surface`, `unknown`
- 아이 이름, 앱 이름, package, raw session history, 허용 앱 원문 목록, PIN 원문/길이/세부값은 부모 모드 payload에 넣지 않는다.

### 긴급해제 설정 변경

긴급해제 설정 변경의 제품/QA 계약은 `docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md`(#694)를 source of truth로 본다. 현재 `EmergencyUnlockSettingsViewModel`은 `EmergencyUnlockSettingsScreen` screen_view와 아래 설정 변경/manual reset 이벤트를 기록한다. 단, GA4 Admin 등록, release/tag/Play deploy, 14일/30일 readback 전에는 live event 0건을 adoption 부재로 해석하지 않고 결론을 보류한다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `emergency_unlock_settings_changed` | `setting_name`, `value_bucket`, `refill_mode`, `duration_count_bucket`, `source` | 긴급해제 기능 ON/OFF, daily limit, duration option, reason required, refill mode 같은 설정 변경 |
| `emergency_unlock_manual_reset_requested` | `refill_mode`, `remaining_unlocks_bucket`, `source`, `reset_result?` | manual refill mode에서 수동 회복 요청 |

현재 enum/bucket 계약:

- `setting_name`: `enabled`, `daily_limit`, `duration_options`, `reason_required`, `refill_mode`
- `value_bucket`: setting별 `on/off`, `1/2/3/4_plus`, `none/short_only/mixed/long_included`, `daily/manual`
- `refill_mode`: `daily`, `manual`, `not_applicable`
- `duration_count_bucket`: `0`, `1`, `2_3`, `4_plus`, `not_applicable`
- `remaining_unlocks_bucket`: `0`, `1`, `2`, `3_plus`, `unknown`
- `source`: `menu`
- `reset_result`: `requested`, `completed`, `unavailable` (optional; 결과 구분이 안전할 때만 추가)
- custom reason 원문, reason display label/custom text, 앱 package/name/list, raw lock/session history, raw timestamp, `manualResetAtMillis` 원문, 설정 snapshot dump는 payload와 GA4 등록 대상에서 금지한다.

### 광고 / 수익화

AdMob 배너 노출/클릭/수익 이벤트는 `TrackedBannerAd.kt`의 전용 contract가 source of truth다. PR #563 이후 `:core:kds`는 AdMob SDK runtime을 직접 소유하지 않으며, KDS는 디자인 primitive/theme boundary로만 본다. 광고 제거 관심도 실험 이벤트는 `KeepAnalytics.kt` / `FirebaseKeepAnalytics.kt` / `FirebaseKeepAnalyticsTest.kt`에 코드 계약이 추가됐고, 2026-06-04 code-lane에서 `MenuScreen.kt` 메뉴/설정 CTA가 첫 안전 표면으로 연결됐다. 실험 판단 전에는 GA4 Admin 등록 상태와 release/tag/Play 배포 후 14일 관측 창을 먼저 확인한다.

| 이벤트명 | 주요 파라미터 | 설명 |
| --- | --- | --- |
| `ad_banner_impression` | `screen_name`, `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id` | Stopit 앱 배너 광고 노출. SDK 자동 `ad_impression`과 섞지 않는다. |
| `ad_banner_click` | `screen_name`, `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id` | Stopit 앱 배너 광고 클릭. SDK 자동 `ad_click`과 섞지 않는다. |
| `ad_banner_revenue` | `screen_name`, `screen_context`, `ad_placement`, `ad_format`, `ad_unit_id`, `ad_currency`, `ad_precision_type`, `ad_value_micros` | Stopit 앱 배너 광고 수익 발생. SDK 자동 `ad_revenue`와 섞지 않는다. |
| `monetization_interest_shown` | `interest_context`, `interest_surface`, `interest_variant?`, `purchase_available?` | 광고 제거/수익화 관심도 CTA 노출 |
| `monetization_interest_clicked` | `interest_context`, `interest_surface`, `interest_variant?`, `purchase_available?` | 광고 제거/수익화 관심도 CTA 클릭 |
| `support_contact_started` | `surface` | 문의/지원 연락 흐름 시작. 현재 `surface=menu`만 허용하며 앱 목록·루틴·잠금 기록·사용자 입력 원문은 기록하지 않는다. |
| `support_contact_fallback_used` | `surface`, `fallback_type` | 이메일 앱 미설치 등으로 기본 메일 intent가 열리지 않아 fallback을 사용. 현재 `fallback_type=clipboard`만 허용한다. |

운영 원칙:

- `(not set)` `adUnitName` 또는 수익 해석 오류가 보이면 먼저 `TrackedBannerAd` 적용 화면과 아래 파라미터 계약을 확인한다.
- 광고 성과 자체는 `docs/ADMOB_MONETIZATION_RUNBOOK.md`를, 이벤트/파라미터 명세는 이 문서를 source of truth로 본다.
- `screen_name`은 기존 `screen_view` 계약의 canonical 화면명(`RoutineScreen`, `LockScreen` 등)과 일치해야 한다.
- GA4/AdMob의 `adUnitName` 차원과 앱 custom parameter `ad_unit_id`는 같은 필드가 아니다. `publisherAdImpressions`/`adUnitName` 보고서와 `customEvent:ad_placement` 보고서는 따로 해석하고, 합산하거나 서로 대체하지 않는다.
- 2026-06-01 live preflight 기준 광고 custom dimensions/metrics는 GA4 metadata에 등록되어 있으므로, 이후 `(not set)` 원인은 단순 Admin 등록 누락보다 SDK 자동 이벤트와 앱 custom event의 이벤트명/필터 충돌 가능성을 먼저 본다.
- 광고 제거 관심도 실험은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 1차 실험 계약을 따른다. `monetization_interest_context` 같은 별도 이벤트를 만들지 않고, `shown`/`clicked` 이벤트에 `interest_context` 파라미터를 붙인다.
- 결제 기능이 실제로 없을 때는 `purchase_available=false`로 기록하고, 이 이벤트를 구매 전환이 아니라 관심도 신호로만 해석한다.

## 주요 파라미터 사전

| 파라미터 | 의미 |
| --- | --- |
| `step_name` | 온보딩 단계 이름 (`intro`, `permission`, `notification`, `select_app`) |
| `permission_name` | 권한 종류 (`accessibility`, `notifications`) |
| `outcome` | 권한 결과 (`granted`, `denied`, `settings_opened`) |
| `source` | 이벤트 발생 출처 (`onboarding`, `home`, `home_timer`, `routine` 등) |
| `block_source` | 차단 발생 출처 (`manual_keep`, `timed_lock`, `routine`, `goal_lock`, `parent_mode`) |
| `blocked_app_package` | legacy/deprecated. 차단된 앱 패키지명 원문이며 #611에 따라 GA4 payload/custom dimension 신규 등록 대상에서 제외한다. PR #617 이후 신규 payload가 아니라 historical/legacy baseline으로만 취급한다. |
| `blocked_app_category_bucket` | 차단된 앱의 privacy-safe category bucket (`social`, `video`, `game`, `communication`, `shopping`, `browser`, `productivity`, `unknown`) |
| `selected_app_count` | 선택된 앱 개수 |
| `is_onboarding` | 온보딩 컨텍스트 여부 |
| `is_routine` | 루틴 기반 세션 여부 |
| `end_reason` | 세션 종료 이유 |
| `reason` | 긴급해제/등록 실패/스킵의 이유 |
| `error` | 리뷰 프롬프트 실패 이유 |
| `period_type` | 집중 요약 공유 대상 기간 (`week`) |
| `session_count_bucket` | 집중 요약 공유 세션 수 bucket (`1`, `2_3`, `4_6`, `7_plus`) |
| `duration_minutes_bucket` | 집중 요약 공유 총 시간 bucket (`1_29`, `30_59`, `60_119`, `120_239`, `240_plus`) 및 부모 모드 시간 선택 bucket (`1_9`, `10`, `11_20`, `21_30`, `31_60`, `61_plus`). 이벤트별 문맥에 맞는 bucket 계약을 따른다. |
| `allowed_app_count_bucket` | 부모 모드 허용 앱 수 bucket (`1`, `2_3`, `4_6`, `7_plus`) |
| `pin_result` | 부모 모드 보호자 PIN 결과 enum (`success`, `failure`, `not_configured`) |
| `extension_minutes_bucket` | 부모 모드 연장 시간 bucket (`1_9`, `10`, `11_20`, `21_30`, `31_plus`) |
| `block_context` | 부모 모드 차단/우회 surface enum (`disallowed_app`, `settings_surface`, `recent_apps`, `notification_surface`, `unknown`) |
| `template_category` | 루틴 템플릿 공유용 비민감 카테고리 (`study`, `work`, `night_focus`, `custom`) |
| `repeat_days_bucket` | 루틴 템플릿 반복 요일 bucket (`weekday`, `weekend`, `daily`, `custom_days`, `none`) |
| `time_window_bucket` | 루틴 템플릿 시간대 bucket (`morning`, `afternoon`, `evening`, `night`, `overnight`, `custom_window`) |
| `routine_name_included` | 공유 payload에 루틴 이름을 사용자가 명시적으로 포함했는지 여부. 이름 원문은 기록하지 않는다. |
| `entry_surface` | 목표 잠금 생성 플로우 진입 표면 (`home`, `routine`, `menu`, `goal_lock_detail`) |
| `duration_selection_type` | 목표 잠금 기간 선택 방식 (`preset_days`, `custom_days`, `end_date`) |
| `lock_mode` | 목표 잠금 방식 (`all_day`, `scheduled`) |
| `selected_app_count_bucket` | 목표 잠금 선택 앱 수 bucket (`1`, `2_3`, `4_6`, `7_plus`) |
| `goal_name_type` | 목표 이름 preset/custom 분류. 목표 이름 원문은 기록하지 않는다. |
| `duration_days_bucket` | 목표 잠금 총 기간 bucket (`1_6`, `7`, `8_14`, `15_30`, `31_plus`) |
| `elapsed_days_bucket` | 조기 종료 시 경과일 bucket (`0`, `1_2`, `3_6`, `7_14`, `15_plus`) |
| `changed_field` | 목표 잠금 수정 필드 enum (`duration`, `apps`, `schedule`, `name`, `lock_mode`) |
| `setting_name` | 긴급해제 설정 변경 축 enum (`enabled`, `daily_limit`, `duration_options`, `reason_required`, `refill_mode`) |
| `value_bucket` | 긴급해제 설정 변경 값 bucket. 이벤트별 source of truth는 `docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md`를 따른다. |
| `duration_count_bucket` | 긴급해제 duration option 개수 bucket (`0`, `1`, `2_3`, `4_plus`, `not_applicable`) |
| `remaining_unlocks_bucket` | 긴급해제 manual reset 전 남은 횟수 bucket (`0`, `1`, `2`, `3_plus`, `unknown`) |
| `refill_mode` | 긴급해제 refill 방식 enum (`daily`, `manual`, `not_applicable`) |
| `reset_result` | 긴급해제 manual reset 결과 enum 후보 (`requested`, `completed`, `unavailable`) |
| `elapsed_since_first_open_seconds` | 첫 실행 후 경과 초 |
| `routine_id` | 루틴 식별자 |
| `goal_lock_id` | 목표 잠금 식별자. 목표 이름/app package/app label 원문이 아니라 내부 id만 전달하며, goal-lock source 차단성과 디버깅에만 사용한다. |
| `screen_name` | 광고가 발생한 canonical 화면명 |
| `screen_context` | 같은 화면 안에서의 광고 문맥 (`empty_state`, `inline`, `footer` 등) |
| `ad_placement` | 제품 관점에서의 광고 위치 식별자 |
| `ad_format` | 광고 형식 (`banner` 등) |
| `ad_unit_id` | 실제 AdMob ad unit id |
| `ad_currency` | 수익 통화 코드 |
| `ad_precision_type` | AdMob가 제공한 수익 정밀도 (`estimated`, `precise`, `publisher_provided`, `unknown`) |
| `ad_value_micros` | 마이크로 단위 광고 수익 |
| `interest_context` | 수익화 관심도 CTA가 놓인 제품 문맥 (`menu_settings`, `home_secondary`, `ad_management` 등) |
| `interest_surface` | 수익화 관심도 CTA 노출 표면 (`menu`, `home`, `settings` 등) |
| `interest_variant` | 수익화 관심도 CTA copy/실험 variant (`default` 등) |
| `purchase_available` | 실제 결제 가능 여부. 결제 구현 전 관심도 측정은 `false` |
| `surface` | 루틴 생성 CTA 노출 표면 (`home`, `lock_history`, `post_block_success`) |
| `activation_stage` | 루틴 생성 CTA 대상 사용자의 활성화 단계 (`post_first_core_action`, `returning_blocked_user`) |
| `has_routine` | 루틴 생성 CTA 대상자의 루틴 보유 여부. #455 MVP에서는 `false`만 허용 |
| `cta_variant` | 루틴 생성 CTA copy/placement 실험 variant (`default` 등) |
| `suggestion_reason` | 반복 차단 기반 루틴 추천 이유 (`repeat_block_time_bucket`, `repeat_block_day_time`, `rapid_retry`) |
| `time_bucket` | 반복 차단 추천 시간대 bucket (`morning`, `afternoon`, `evening`, `night`, `overnight`) |
| `day_type` | 반복 차단 추천 요일 유형 (`weekday`, `weekend`, `daily`, `custom_days`) |
| `category_bucket` | 반복 차단 추천 앱 카테고리 bucket (`social`, `video`, `game`, `shopping`, `browser`, `unknown`) |
| `repeat_count_bucket` | 반복 차단 추천 강도 bucket (`3_5`, `6_10`, `10_plus`) |
| `routine_coverage_state` | 기존 루틴이 추천 패턴을 커버하는지 여부 (`not_covered`, `partially_covered`; `covered`는 추천 노출 실패) |
| `suggestion_variant` | 반복 차단 루틴 추천 copy/placement variant (`default` 등) |

## User property 계약

`routines_count`는 live metadata에서 확인된 `customUser` dimension이므로, 이 값의 의미를 이벤트 파라미터와 분리해서 명시한다. activation/review/ad 관련 `customEvent:*` 조회성은 아래 GA4 registration 계약과 운영 메모를 따른다.

| user property | 코드 source of truth | 언제 갱신되는가 | 의미 / 해석 주의사항 |
| --- | --- | --- | --- |
| `routines_count` | 현재 구현은 `app/src/main/java/com/uiery/keep/feature/routine/RoutineViewModel.kt`; #479 목표 source of truth는 `KeepAnalytics` 계층의 중앙 API/상수 + 앱/Home/restore 공통 sync 경로 | 현재는 루틴 목록을 구독해 `routines` 상태를 반영하고 `storeRoutine(...)`까지 끝낸 뒤 `analytics.setUserProperty("routines_count", routines.size.toString())`를 호출할 때. #479 이후에는 앱 실행/Home 진입, 루틴 생성/수정/삭제, backup/restore 또는 boot rehydrate 이후에도 `0` 또는 실제 Room count를 명시적으로 설정해야 한다. | 현재 사용자가 보유한 루틴 개수의 스냅샷이다. 이벤트처럼 시점별 히스토리가 아니라 최신 상태를 덮어쓰므로, `activeUsers` 분모 대비 “루틴 1개 이상 보유 사용자 비율” 같은 보조 지표 해석에만 쓰고 특정 세션/화면 전환의 직접 원인처럼 과해석하지 않는다. #479 완료 전에는 `routines_count=(not set)` coverage gap을 별도 cohort로 유지한다. |

운영 원칙:

- `routines_count`는 #13에서 **이미 조회 가능한 customUser 축**이므로, `customEvent:*` 등록이 비어 있어도 루틴 보유 분포 해석에는 사용할 수 있다.
- 단, #479의 `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md`가 정의한 것처럼 조회 가능성과 커버리지는 다르다. 2026-06-03 기준 `(not set)` activeUsers가 `560 / 865 = 64.7%`였으므로, coverage 개선 포함 버전의 release/tag/Play deploy + D+14/D+30 readback 전에는 `0`/`>=1` cohort만 전체 retention 결론으로 일반화하지 않는다.
- 다만 user property 특성상 과거 시점 복원이 어렵기 때문에, 코호트/퍼널 결론은 `first_lock_configured`, `first_core_action_completed`, `app_block_intercepted` 같은 이벤트와 함께 본다.
- product/metrics 문서에서 `루틴 생성 사용자 비율`을 언급할 때는 이 계약을 source of truth로 본다.

## GA4 custom dimension / metric 등록 계약

`screen_view`와 이벤트명이 코드에 있어도, 주요 파라미터가 GA4 커스텀 차원/지표로 등록되지 않으면 대시보드와 cron 분석에서 조회할 수 없다. 아래 표를 기본 운영 계약으로 본다. 실제 GA4 Admin 등록 절차, registration ledger, metadata 증적 포맷은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 본다.

### 우선 등록할 이벤트 차원

| 분류 | GA4 등록 이름 예시 | 코드 파라미터 | 주 사용 이벤트 | 왜 필요한가 |
| --- | --- | --- | --- | --- |
| Required | `step_name` | `step_name` | `onboarding_step_view`, `onboarding_step_complete`, `permission_outcome` | 온보딩 단계별 이탈/완료 분석 |
| Required | `permission_name` | `permission_name` | `permission_outcome` | 접근성/알림 권한 병목 분리 |
| Required | `outcome` | `outcome` | `permission_outcome` | granted / denied / settings_opened 비교 |
| Required | `source` | `source` | `first_lock_configured`, `lock_session_start`, `lock_session_end`, `emergency_unlock_used` | 온보딩/홈/루틴 출처별 행동 비교 |
| Required | `block_source` | `block_source` | `app_block_intercepted` | manual_keep / timed_lock / routine / goal_lock / parent_mode 차단 성공 비교 |
| Required | `blocked_app_category_bucket` | `blocked_app_category_bucket` | `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` | 실제 차단 가치가 어느 앱 카테고리에서 발생하는지 privacy-safe bucket으로 확인. `blocked_app_package` 원문은 #611에 따라 등록 금지 |
| Required | `selected_app_count` | `selected_app_count` | `app_selection_completed`, `first_lock_configured` | 앱 선택량과 활성화 상관관계 확인 |
| Required | `is_onboarding` | `is_onboarding` | `app_selection_completed` | 온보딩 vs 이후 설정 행동 분리 |
| Required | `is_routine` | `is_routine` | `lock_session_start`, `lock_session_end` | 루틴 세션과 수동 세션 분리 |
| Required | `end_reason` | `end_reason` | `lock_session_end` | 세션 종료 사유 비교 |
| Required | `reason` | `reason` | `emergency_unlock_completed`, `device_registration_skipped`, `review_prompt_skipped` | 긴급해제/스킵/리뷰 보류 이유 분석. `device_registration_failed`는 제거된 legacy 이벤트이므로 backend registration 재도입 전에는 지표 축으로 해석하지 않는다. |
| Required | `period_type` | `period_type` | `focus_summary_share_tapped`, `focus_summary_share_sheet_opened`, `focus_summary_share_failed` | 공유 지표를 주간 요약 기준으로 해석 |
| Required | `session_count_bucket` | `session_count_bucket` | `focus_summary_share_tapped`, `focus_summary_share_sheet_opened` | 세션 수별 공유 의도 비교(privacy-safe bucket) |
| Required | `duration_minutes_bucket` | `duration_minutes_bucket` | `focus_summary_share_tapped`, `focus_summary_share_sheet_opened`, `parent_mode_duration_selected`, `parent_mode_started`, `parent_mode_completed` | 집중 요약/부모 모드 duration bucket 비교. 각 이벤트별 bucket 계약을 따른다 |
| Required | `template_category` | `template_category` | `routine_template_share_tapped`, `routine_template_share_sheet_opened`, `routine_template_share_failed` | 루틴 템플릿 카테고리별 공유 의도 비교(privacy-safe enum) |
| Required | `repeat_days_bucket` | `repeat_days_bucket` | `routine_template_share_tapped`, `routine_template_share_sheet_opened` | 요일 패턴별 공유 의도 비교(privacy-safe bucket) |
| Required | `time_window_bucket` | `time_window_bucket` | `routine_template_share_tapped`, `routine_template_share_sheet_opened` | 시간대 패턴별 공유 의도 비교(privacy-safe bucket) |
| Required | `routine_name_included` | `routine_name_included` | `routine_template_share_tapped`, `routine_template_share_sheet_opened` | 이름 opt-in 여부와 공유 전환 비교. 이름 원문은 금지 |
| Required | `screen_context` | `screen_context` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 같은 화면 안 Stopit 앱 배너 광고 문맥별 성과 비교 |
| Required | `ad_placement` | `ad_placement` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 제품 위치별 Stopit 앱 배너 CTR/eCPM 감사 |
| Required | `ad_format` | `ad_format` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 광고 형식별 성과 분리 |
| Required | `ad_unit_id` | `ad_unit_id` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | `(not set)` 원인 추적과 단위별 매핑 |
| Required | `interest_context` | `interest_context` | `monetization_interest_shown`, `monetization_interest_clicked` | 광고 제거/수익화 관심도 CTA의 문맥별 반응 비교 |
| Required | `interest_surface` | `interest_surface` | `monetization_interest_shown`, `monetization_interest_clicked` | 안전한 노출 표면별 관심 클릭률 비교 |
| Required | `surface` | `surface` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed`, `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied`, `support_contact_started`, `support_contact_fallback_used` | 루틴 CTA/추천/지원 연락의 안전한 노출 표면별 반응 비교 |
| Required | `fallback_type` | `fallback_type` | `support_contact_fallback_used` | 이메일 앱 미설치 fallback 방식. 현재 `clipboard`만 허용하며 진단 payload 원문은 analytics에 기록하지 않는다. |
| Required | `activation_stage` | `activation_stage` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | `post_first_core_action` vs returning blocked user 맥락 분리 |
| Required | `has_routine` | `has_routine` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | 루틴 보유자 오노출을 감지하고 MVP 대상(`false`)만 분리 |
| Required | `suggestion_reason` | `suggestion_reason` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | 반복 차단 기반 루틴 추천 이유 비교 |
| Required | `time_bucket` | `time_bucket` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | 추천 시간대별 반응 비교. raw timestamp 금지 |
| Required | `day_type` | `day_type` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | weekday/weekend/daily/custom 추천 패턴 비교 |
| Required | `category_bucket` | `category_bucket` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | 앱 category bucket별 추천 반응 비교. 앱 이름/package 금지 |
| Required | `repeat_count_bucket` | `repeat_count_bucket` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | 반복 강도별 추천 반응 비교. raw retry count 금지 |
| Required | `routine_coverage_state` | `routine_coverage_state` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | 기존 루틴 커버리지 충돌/오노출 감지 |
| Required | `allowed_app_count_bucket` | `allowed_app_count_bucket` | `parent_mode_allowed_apps_selected`, `parent_mode_started` | 부모 모드 허용 앱 개수별 setup/시작 전환 비교. 앱 이름/package 원문 금지 |
| Required | `pin_result` | `pin_result` | `parent_mode_unlocked_by_pin` | 보호자 PIN 해제/연장 성공·실패 UX guardrail 확인. PIN 원문/길이/세부값 금지 |
| Required | `block_context` | `block_context` | `parent_mode_block_intercepted` | 부모 모드 중 허용되지 않은 앱/설정/최근 앱/알림 surface 우회 리스크 분리 |
| Recommended | `error` | `error` | `review_prompt_failed` | 리뷰 프롬프트 실패 원인 파악 |
| Recommended | `blocking_mode` | `blocking_mode` | `first_core_action_completed`, `core_action_completed` | 첫 핵심 행동과 반복 핵심 행동의 모드 비교 |
| Recommended | `routine_id` | `routine_id` | `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` | 특정 루틴 성과/문제 추적 |
| Recommended | `goal_lock_id` | `goal_lock_id` | `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` | 특정 목표 잠금 차단성과/문제 추적. 목표 이름·앱 label 원문은 금지 |
| Recommended | `screen_name` | `screen_name` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 광고 성과와 화면 계약 드리프트 동시 분석 |
| Recommended | `ad_currency` | `ad_currency` | `ad_banner_revenue` | 통화 코드 확인 |
| Recommended | `ad_precision_type` | `ad_precision_type` | `ad_banner_revenue` | 추정 수익 vs 정밀 수익 구분 |
| Recommended | `interest_variant` | `interest_variant` | `monetization_interest_shown`, `monetization_interest_clicked` | CTA copy/variant 비교가 필요할 때 |
| Recommended | `purchase_available` | `purchase_available` | `monetization_interest_shown`, `monetization_interest_clicked` | 결제 미구현 관심도 측정과 실제 구매 가능 상태를 분리 |
| Recommended | `cta_variant` | `cta_variant` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | 루틴 생성 CTA copy/placement 비교가 필요할 때 |
| Recommended | `suggestion_variant` | `suggestion_variant` | `repeat_block_routine_suggestion_shown`, `repeat_block_routine_suggestion_clicked`, `repeat_block_routine_suggestion_dismissed`, `repeat_block_routine_suggestion_applied` | 반복 차단 루틴 추천 copy/placement 비교가 필요할 때 |
| Recommended | `extension_minutes_bucket` | `extension_minutes_bucket` | `parent_mode_extended` | 보호자 PIN 확인 후 연장 시간 분포가 필요할 때 |

### 필요 시 등록할 이벤트 지표

| 분류 | GA4 등록 이름 예시 | 코드 파라미터 | 주 사용 이벤트 | 왜 필요한가 |
| --- | --- | --- | --- | --- |
| Recommended | `selected_app_count` | `selected_app_count` | `app_selection_completed`, `first_lock_configured` | 선택 앱 수 분포/평균 분석 |
| Recommended | `scheduled_duration_minutes` | `scheduled_duration_minutes` | `lock_scheduled` | 루틴/타이머 예약 길이 분석 |
| Recommended | `duration_minutes` | `duration_minutes` | `emergency_unlock_completed` | 긴급해제 사용 길이 분포 분석 |
| Recommended | `remaining_unlocks` | `remaining_unlocks` | `emergency_unlock_completed` | 잔여 긴급해제 수와 재사용 패턴 분석 |
| Recommended | `elapsed_since_first_open_seconds` | `elapsed_since_first_open_seconds` | `first_core_action_completed`, `core_action_completed` | 첫 가치 도달 시간 분석 |
| Recommended | `ad_value_micros` | `ad_value_micros` | `ad_banner_revenue` | placement/context별 수익 분포 재집계 |

운영 원칙:

- `Required` 항목이 빠진 상태에서는 해당 퍼널/문제에 대한 결론 confidence를 낮춘다.
- `Recommended` 항목은 분석 목적이 생기면 등록하되, 등록 전에는 문서/이슈에서 “GA4에서 직접 조회 불가”를 명시한다.
- 새 이벤트를 추가할 때는 코드 PR과 함께 이 표를 갱신한다.

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

주의:

- Play In-App Review는 `accepted` / `dismissed` 같은 사용자 결과를 앱에 돌려주지 않는다.
- 따라서 앱 이벤트는 노출/스킵/실패까지만 source of truth로 보고, 실제 리뷰 결과는 Play Console에서 후행 확인한다.

## 검증 명령

### 로컬 단위 테스트

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests com.uiery.keep.analytics.FirebaseKeepAnalyticsTest
./gradlew :app:testDevDebugUnitTest --tests com.uiery.keep.feature.onboarding.OnboardingAnalyticsViewModelTest
./gradlew :app:testDevDebugUnitTest --tests com.uiery.keep.feature.lock.LockViewModelTest
```

### GA4 metadata로 등록 상태 확인

```bash
cd <repo-root>
python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '/path/to/ga4-service-account.json'

creds = service_account.Credentials.from_service_account_file(
    CREDENTIAL_PATH,
    scopes=['https://www.googleapis.com/auth/analytics.readonly'],
)
session = AuthorizedSession(creds)
response = session.get(
    f'https://analyticsdata.googleapis.com/v1beta/properties/{PROPERTY_ID}/metadata'
)
print(response.status_code)
metadata = response.json()

print('\nCustom dimensions')
for dimension in metadata.get('dimensions', []):
    api_name = dimension.get('apiName', '')
    if api_name.startswith('customEvent:') or api_name.startswith('customUser:'):
        print(api_name, '|', dimension.get('uiName'))

print('\nCustom metrics')
for metric in metadata.get('metrics', []):
    api_name = metric.get('apiName', '')
    if api_name.startswith('customEvent:'):
        print(api_name, '|', metric.get('uiName'))
PY
```

### GA4 screen name 확인

```bash
cd <repo-root>
python3 - <<'PY'
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PROPERTY_ID = '502544175'
CREDENTIAL_PATH = '/path/to/ga4-service-account.json'

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

## 운영 런북

### 신규 이벤트/파라미터를 추가했을 때

1. `KeepAnalytics.kt` / `FirebaseKeepAnalytics.kt` / 관련 테스트를 먼저 확인한다.
2. 이 문서의 이벤트 딕셔너리와 등록 계약 표를 같이 갱신한다.
3. 필요한 차원/지표가 `Required`면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 ledger/절차에 따라 GA4 Admin 등록과 metadata 확인을 끝내기 전까지 대시보드 결론을 보류한다.
4. 배포 후 14일 창으로 `(not set)` 비율과 새 파라미터 조회 가능 여부를 재측정한다.

### `(not set)` 또는 조회 불가가 보일 때 triage 순서

1. `screen_view` 누락인지 확인한다.
2. 코드 파라미터 이름과 문서 이름이 일치하는지 확인한다.
3. GA4 metadata에서 해당 `customEvent:*` 차원/지표가 실제 등록됐는지 확인한다.
4. `runReport`가 `400 INVALID_ARGUMENT`와 함께 `Field customEvent:... is not a valid dimension`을 반환하면, no-data가 아니라 **미등록 상태**로 분류하고 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 registration follow-through를 우선한다.
5. 이벤트가 최근 버전에서만 추가된 경우 `appVersion` 세그먼트로 다시 본다.
6. 그래도 불명확하면 제품 결론보다 계측 개선 이슈를 먼저 연다.

### 2026-05-29 live 점검 메모 + 2026-06-01 광고 보정

- 2026-05-29 metadata 기준 조회 가능한 custom dimension은 `customUser:routines_count`만 확인됐다.
- 당시 activation (`customEvent:permission_name`, `customEvent:source`), review (`customEvent:reason`), monetization (`customEvent:ad_placement`) smoke query는 모두 `400 INVALID_ARGUMENT` / `not a valid dimension`으로 실패해, 병목이 no-data가 아니라 **미등록 쿼리 축**임을 확인했다. 단, review `customEvent:reason`은 2026-06-02T18:06:45Z #307 live 재조회에서 등록/조회 가능해졌다.
- 2026-06-01 #16 preflight에서는 광고 관련 `customEvent:ad_unit_id`, `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_format`, `customEvent:ad_value_micros`, `customEvent:screen_name`이 metadata에 등록된 것으로 확인됐다.
- 광고 쪽은 이제 “전체 미등록”이 아니라 PR #293 이후 `ad_banner_impression` / `ad_banner_click` / `ad_banner_revenue` coverage와 placement별 성과를 다시 봐야 하는 상태로 해석한다. 단, 2026-06-02 확인 기준 최신 production tag `v1.7.7`은 PR #293 split commit을 포함하지 않으므로 post-split 14일 창은 아직 시작되지 않았다. legacy `ad_impression` / `ad_click` / `ad_revenue` breakdown과 `v1.7.7` production 광고 데이터는 PR #293 이전 SDK 자동 이벤트와 앱 custom event source split baseline으로만 본다.
- 활성화 축과 review `customEvent:error`는 별도 metadata/runReport 확인 전까지 registration gap으로 유지한다. review `customEvent:reason`은 #307 skip reason 판단에 사용할 수 있다.
- 최근 14일 `screen_view`는 총 `13,154`건이고, `(not set)` `9,473`건 + 빈 `unifiedScreenName` `801`건으로 합계 `10,274 / 13,154 = 78.1%`다.
- 이 screen 품질 baseline은 PR #296의 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen` 및 PR #318의 dev/debug `DevToolScreen` 보강 전 값이다. 네 화면은 develop에서 explicit `screen_view` 계약이 보강됐고, PR #358 merge commit `6ceaecc4`가 release-boundary 해석을 문서화했으므로, 같은 화면을 다시 code-lane 후보로 올리기 전 `PR #296/#318/#358` 포함 버전 배포 후 14일 창으로 재측정한다. `DevToolScreen`은 dev/debug 내부 진단 surface라 production 사용자 screen 품질 분모와 분리해서 본다.
- 2026-06-03 09:12 KST live smoke에서는 최근 14일 combined gap이 `13,780 / 22,584 = 61.0%`로 조회됐다. 다만 PR #296/#318 merge commit은 아직 `origin/main`/production tag `v1.7.7`에 없으므로 이 수치는 **post-fix 성과가 아니라 release boundary 전 중간 smoke**로만 기록한다. #13 closure는 release/tag/Play deploy 후 **D+14 screen quality 재측정**으로 판단한다.
- 2026-06-08T23:09:54Z metrics snapshot의 30일 `screen_view` 합산에서는 `(not set)+blank` gap이 `27,369 / 44,376 = 61.7%`였고 최신 관측 production version `1.7.7` active share도 `232 / 806 = 28.8%`(`주의`)였다. 이 값은 위 14일 query를 대체하지 않지만, #13 closure가 여전히 release/tag/Play deploy + D+14 재측정 경계에 있음을 확인하는 guardrail로 둔다.
- 온보딩 화면명은 보이지만 전체 계측 품질 병목은 여전히 해소되지 않았다.
- 실제 GA4 Admin 등록 우선순위, registration ledger, issue/PR handoff 형식은 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`를 source of truth로 본다.

## 운영 메모

- 신규 화면/이벤트 추가 시 이 문서와 테스트를 같이 갱신한다.
- 커스텀 차원 등록 상태가 바뀌면 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 registration ledger / metadata 증적 / 재측정 표와 `docs/METRICS_ANALYSIS.md`의 조회 가이드를 같이 업데이트한다.
- `(not set)` 비율 재측정 기본 창은 배포 후 14일이다.
