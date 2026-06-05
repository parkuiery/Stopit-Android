# GA4 커스텀 차원/지표 등록 운영 런북

이 문서는 open issue #13 `GA4 계측 품질 및 이벤트 딕셔너리 개선`의 **GA4 Admin 수동 등록 / 증적 수집 / 사후 재측정** 기준을 한곳에 고정한다.

핵심 목적은 두 가지다.

1. 앱 코드와 `docs/ANALYTICS_EVENT_DICTIONARY.md`에 정의된 파라미터가 **GA4에서 실제 조회 가능한 상태인지** 운영적으로 확인한다.
2. docs lane이 만들 수 있는 저장소 산출물과, GA4 Admin / 배포 후 관측이 필요한 **외부 경계**를 명확히 구분한다.

이 문서만으로 #13을 닫지는 않는다. 하지만 `customEvent:*` 차원/지표 등록, metadata 증적, 14일 재측정 계약까지 정리해 두면 이후 metrics/code lane이 같은 문제를 반복해서 재해석하지 않아도 된다.

## 관련 source of truth

- 이벤트/파라미터 계약: `docs/ANALYTICS_EVENT_DICTIONARY.md`
- 분석/이슈화 절차: `docs/METRICS_ANALYSIS.md`
- 제품 대시보드와 우선순위: `docs/PRODUCT_METRICS_DASHBOARD.md`
- `routines_count` user property coverage 계약: `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md` (#479)
- LockHistory 성과 리포트 UX/analytics 계약: `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md` (#465)
- 버전 채택률/최신 cohort 판독: `docs/VERSION_ADOPTION_METRICS_GATE.md`
- open issue: `#13`
- 앱 코드 상수: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`
- Firebase 구현: `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt`
- 광고 계측 구현: `app/src/main/java/com/uiery/keep/analytics/TrackedBannerAd.kt`

## 왜 별도 런북이 필요한가

`docs/ANALYTICS_EVENT_DICTIONARY.md`에는 어떤 파라미터를 등록해야 하는지 이미 적혀 있다. 하지만 실제 운영에서는 아래가 별도 문제로 남는다.

- 어떤 항목이 **GA4 Admin에서 이미 등록됐는지**
- 어떤 항목이 아직 **코드에는 있지만 조회 불가 상태**인지
- docs lane이 어디까지 정리하면 충분하고, 그 다음은 누가/어떻게 수동 확인해야 하는지
- 배포 후 14일 재측정을 어떤 포맷으로 남길지

현재 기준으로는 이 운영 계약이 분산되어 있어, 같은 #13을 다시 볼 때마다 "문서 미정리"와 "GA4 Admin 미반영"을 혼동하기 쉽다.

## 현재 상태 요약

2026-05-29 live 점검 메모 기준:

- `customUser:routines_count`만 metadata에서 확인됨. 단 #479의 `docs/ROUTINES_COUNT_COVERAGE_CONTRACT.md` 기준으로 metadata 조회 가능성과 active user coverage는 분리한다.
- 활성화/리뷰용 `customEvent:*` 차원/지표는 아직 보이지 않음
- 광고용 `customEvent:*`는 아래 2026-06-01 #16 AdMob preflight에서 일부 등록 확인으로 보정됨
- 최근 14일 `screen_view` 총량 `13,154`
- `(not set)` `9,473` + 빈 `unifiedScreenName` `801` = `10,274 / 13,154 = 78.1%`

2026-06-01 #16 AdMob preflight 기준 추가 확인:

- 광고 관련 `customEvent:ad_unit_id`, `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_format`, `customEvent:ad_value_micros`, `customEvent:screen_name`은 metadata에 등록된 상태로 확인됨
- 다만 최근 30일 광고 이벤트 breakdown은 `(not set)`/empty 비중이 커서, 광고 쪽 병목은 단순 GA4 Admin 미등록이 아니라 **SDK 자동 이벤트와 앱 custom event source split / query contract 문제**로 분리한다

2026-06-02 develop 기준 추가 확인:

- PR #296에서 `SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen`의 명시적 `screen_view` 계측이 추가됐다.
- PR #318에서 dev/debug 내부 진단 surface인 `DevToolScreen` 명시적 `screen_view` 계측도 추가됐다.
- 따라서 2026-05-29 screen 품질 baseline은 위 네 화면 보강 전 기준선이다. 같은 화면에 대해 새 code-lane 작업을 다시 열기 전에, PR #296/#318 포함 버전 배포 후 14일 창에서 `(not set)` / blank `unifiedScreenName`가 실제로 남는지 먼저 재측정한다. 이때 최신 버전 active share는 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준으로 함께 판정한다. `DevToolScreen`은 dev/debug 전용 route이므로 production 사용자 screen 품질 판정의 주요 분모로 과대해석하지 않는다.

해석:

- 현재 #13은 단순 문서 부재 문제가 아니라 **GA4 Admin 등록, 이벤트 source split, 배포 후 계측 품질 회복이 아직 모두 끝나지 않은 상태**다.
- 활성화/리뷰 세부 파라미터 결론은 계속 낮은 confidence로 둔다.
- 루틴 보유/미보유 retention 결론도 `routines_count=(not set)` coverage가 큰 동안에는 낮은 confidence로 두고, #479 coverage 개선 포함 버전의 release/tag/Play deploy + D+14/D+30 readback을 기다린다.
- 광고/수익화 결론은 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 AdMob event-source split 계약을 먼저 적용한 뒤 판단한다.

## 권장 등록 순서

한 번에 모든 `customEvent:*`를 등록 목록으로만 남기면 실제 운영에서 우선순위가 다시 흐려진다. #13의 목적은 "계측을 믿을 수 있는 상태"를 만드는 것이므로, 아래 순서로 등록/검증하는 것을 기본값으로 둔다.

### 1순위: 활성화/핵심 가치 판단 복구

- `step_name`
- `permission_name`
- `outcome`
- `source`
- `block_source`
- `blocked_app_package`
- `selected_app_count`
- `is_onboarding`

이 묶음이 먼저 필요한 이유:

- `first_open -> onboarding -> permission -> app_selection_completed -> first_lock_configured -> first_core_action_completed -> app_block_intercepted` 해석 confidence를 올리는 최소 집합이다.
- issue #14 `첫 잠금 활성화 퍼널 개선`의 다음 실행 판단이 이 묶음에 직접 의존한다.

### 2순위: 세션 종료/리뷰/신뢰 흐름

- `is_routine`
- `end_reason`
- `reason`
- `error` (Recommended)

이 묶음이 필요한 이유:

- `lock_session_end`, `emergency_unlock_completed`, `review_prompt_skipped`, `review_prompt_failed` 해석을 위해 종료/실패 사유를 분리해야 한다.
- 리뷰/긴급해제/디바이스 등록 관련 신뢰 이슈는 raw event count만으로는 판단이 어렵다.

### 3순위: 광고/수익화 조회성

- `screen_context`
- `ad_placement`
- `ad_format`
- `ad_unit_id`
- `screen_name` (Recommended)
- `ad_currency` (Recommended)
- `ad_precision_type` (Recommended)
- `ad_value_micros` (Recommended metric)

이 묶음이 필요한 이유:

- issue #16 `AdMob 성과 감사 및 안전한 수익화 실험 설계`에서 `(not set)` / placement / CTR / eCPM 해석을 하려면 광고 파라미터 조회성이 확보되어야 한다.
- 활성화/신뢰보다 우선순위가 낮으므로 기본값은 1·2순위 완료 후다.

### 4순위: 수익화 관심도 실험 조회성

- `interest_context`
- `interest_surface`
- `interest_variant` (Recommended)
- `purchase_available` (Recommended)

이 묶음이 필요한 이유:

- `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 1차 실험인 광고 제거 관심도 측정은 `monetization_interest_shown` / `monetization_interest_clicked`에 이 파라미터를 붙여야 문맥별 클릭률을 계산할 수 있다.
- 단, 실제 결제 구현 전에는 구매 전환이 아니라 관심도 신호로만 해석한다. `purchase_available=false` 상태의 클릭을 매출 전환으로 표현하지 않는다.
- 1·2순위 활성화/신뢰 축과 3순위 광고 source split이 해석 가능한 상태가 된 뒤에 구현/등록하는 것이 기본이다.
- PR #402 merge commit `de142bd34a2729bcbb1e932db70b34d6459ce3b0`으로 메뉴/설정 CTA UI는 `origin/develop`에 연결됐지만, 2026-06-04 확인 기준 `origin/main`에는 없다. 따라서 `customEvent:interest_context` / `customEvent:interest_surface` 등록과 CTA 포함 release/tag/Play deploy 전에는 event 0을 수요 없음으로 해석하지 않는다.

### 5순위: 루틴 템플릿 공유 루프 조회성

- `template_category`
- `repeat_days_bucket`
- `time_window_bucket`
- `routine_name_included`

이 묶음이 필요한 이유:

- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`(#407)의 Android share sheet MVP가 구현된 뒤, 공유 CTA 의도와 share sheet 전환을 privacy-safe enum/bucket 기준으로만 비교하기 위한 최소 집합이다.
- `lockApplications`, package name, 앱 이름, raw session history, 루틴 이름 원문은 GA4 payload와 registration ledger 모두에서 금지한다.
- #407 문서/구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록·metadata 확인·배포 후 14일 관측 전에는 루틴 템플릿 공유의 획득/retention 효과를 낮은 confidence로 둔다.

### 6순위: LockHistory 성과 리포트 조회성

- `period_type`
- `report_state`
- `session_count_bucket`
- `duration_minutes_bucket`
- `top_apps_count_bucket`

이 묶음이 필요한 이유:

- `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`(#465)의 성과 리포트 UX가 구현된 뒤, empty/low-data/has-history 상태별 summary card 노출과 top apps 성과 섹션 조회를 privacy-safe bucket 기준으로 비교하기 위한 최소 집합이다.
- 앱 이름, package name, raw session history, raw timestamp, raw duration은 GA4 payload와 registration ledger 모두에서 금지한다.
- #465 문서/구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록·metadata 확인·release/tag/Play deploy·14일 관측 전에는 `lock_history_*` event 0건을 UX 실패나 수요 없음으로 해석하지 않는다.

### 7순위: 목표 잠금 조회성

- `duration_selection_type`
- `lock_mode`
- `selected_app_count_bucket`
- `goal_name_type`
- `duration_days_bucket`
- `elapsed_days_bucket`

이 묶음이 필요한 이유:

- `docs/GOAL_LOCK_MVP.md`(#417)의 기간 기반 장기 잠금이 구현된 뒤, all-day vs scheduled 선택, 생성 완료율, 자동 완료율, 조기 종료율을 enum/bucket 기준으로 비교하기 위한 최소 집합이다.
- 목표 이름 원문/app package/app label/raw 날짜는 GA4 payload와 registration ledger 모두에서 금지한다.
- #417 문서/구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록·metadata 확인·배포 후 14일 관측 전에는 목표 잠금 유지/완료율과 장기 retention 효과를 낮은 confidence로 둔다.

### 8순위: 루틴 생성 CTA 조회성

- `surface`
- `activation_stage`
- `has_routine`
- `cta_variant`

이 묶음이 필요한 이유:

- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`(#455)의 post-first-core-action soft CTA가 구현된 뒤, 루틴 0개 사용자에게만 안전하게 노출됐는지와 클릭/생성 전환을 비교하기 위한 최소 집합이다.
- 앱 이름, package name, `lockApplications`, raw session history, raw timestamp, `routine_id`는 CTA 이벤트 payload와 registration ledger 모두에서 금지한다.
- #455 문서/구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록·metadata 확인·CTA 포함 release/tag/Play deploy·14일 관측 전에는 루틴 CTA의 retention 효과를 낮은 confidence로 둔다.

### 9순위: 부모 모드 조회성

- `duration_minutes_bucket`
- `allowed_app_count_bucket`
- `pin_result`
- `end_reason`
- `extension_minutes_bucket`
- `block_context`

이 묶음이 필요한 이유:

- `docs/PARENT_MODE_MVP.md`(#471)의 same-device 부모 모드가 구현된 뒤, 사용 시간 선택→허용 앱 선택→시작→시간 만료/PIN 해제/우회 차단을 enum/bucket 기준으로 비교하기 위한 최소 집합이다.
- 아이 이름, 앱 이름/package, raw session history, 허용 앱 원문 목록, PIN 원문/길이/세부값은 GA4 payload와 registration ledger 모두에서 금지한다.
- #471 문서/구현 PR이 event dictionary를 갱신하더라도, GA4 Admin 등록·metadata 확인·배포 후 14일 관측 전에는 부모 모드 setup/완료율과 가족 사용 확장 효과를 낮은 confidence로 둔다.

## Required / Recommended 등록 워크리스트

아래 표는 `docs/ANALYTICS_EVENT_DICTIONARY.md`의 등록 계약을 운영 실행용으로 재구성한 것이다.

### 1) Required 이벤트 차원

| 코드 파라미터 | 주 사용 이벤트 | 현재 상태 | 다음 액션 | 증적 |
| --- | --- | --- | --- | --- |
| `step_name` | `onboarding_step_view`, `onboarding_step_complete`, `permission_outcome` | 미확인/등록 필요 | GA4 Admin custom dimension 등록 후 metadata 확인 | `customEvent:step_name` |
| `permission_name` | `permission_outcome` | 미확인/등록 필요 | 동일 | `customEvent:permission_name` |
| `outcome` | `permission_outcome` | 미확인/등록 필요 | 동일 | `customEvent:outcome` |
| `source` | `first_lock_configured`, `lock_session_start`, `lock_session_end`, `emergency_unlock_used` | 미확인/등록 필요 | 동일 | `customEvent:source` |
| `block_source` | `app_block_intercepted` | 미확인/등록 필요 | 동일 | `customEvent:block_source` |
| `blocked_app_package` | `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` | 미확인/등록 필요 | 동일 | `customEvent:blocked_app_package` |
| `selected_app_count` | `app_selection_completed`, `first_lock_configured` | 미확인/등록 필요 | 동일 | `customEvent:selected_app_count` |
| `is_onboarding` | `app_selection_completed` | 미확인/등록 필요 | 동일 | `customEvent:is_onboarding` |
| `is_routine` | `lock_session_start`, `lock_session_end` | 미확인/등록 필요 | 동일 | `customEvent:is_routine` |
| `end_reason` | `lock_session_end` | 미확인/등록 필요 | 동일 | `customEvent:end_reason` |
| `reason` | `emergency_unlock_completed`, `device_registration_skipped`, `review_prompt_skipped` | 2026-06-02T18:06:45Z metadata 등록/조회 확인 | #307 skip reason 분석에는 사용 가능. activation/다른 trust 이벤트는 event coverage를 별도 확인 | `customEvent:reason` |
| `reason` legacy note | `device_registration_failed` | 현재 코드 API/event constant에서 제거됨 | backend registration 재도입 전에는 GA4 지표 축으로 해석 금지 | 해당 없음 |
| `screen_context` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 2026-06-01 metadata 등록 확인 | PR #293 포함 release/tag/Play deploy 후 14일 재조회 | `customEvent:screen_context` |
| `ad_placement` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 2026-06-01 metadata 등록 확인 | PR #293 포함 release/tag/Play deploy 후 14일 재조회 | `customEvent:ad_placement` |
| `ad_format` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 2026-06-01 metadata 등록 확인 | PR #293 포함 release/tag/Play deploy 후 14일 재조회 | `customEvent:ad_format` |
| `ad_unit_id` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 2026-06-01 metadata 등록 확인 | PR #293 포함 release/tag/Play deploy 후 14일 재조회 | `customEvent:ad_unit_id` |
| `interest_context` | `monetization_interest_shown`, `monetization_interest_clicked` | 2026-06-03 코드 계약 추가, 2026-06-04 메뉴/설정 CTA 연결 / GA4 등록 필요 | CTA 포함 버전 배포 전후로 GA4 Admin 등록 후 metadata 확인 | `customEvent:interest_context` |
| `interest_surface` | `monetization_interest_shown`, `monetization_interest_clicked` | 2026-06-03 코드 계약 추가, 2026-06-04 메뉴/설정 CTA 연결 / GA4 등록 필요 | 동일 | `customEvent:interest_surface` |
| `template_category` | `routine_template_share_tapped`, `routine_template_share_sheet_opened`, `routine_template_share_failed` | #407 코드 계약 추가 / GA4 등록 필요 | 루틴 템플릿 공유 CTA 포함 버전 배포 전후로 GA4 Admin 등록 후 metadata 확인 | `customEvent:template_category` |
| `repeat_days_bucket` | `routine_template_share_tapped`, `routine_template_share_sheet_opened` | #407 코드 계약 추가 / GA4 등록 필요 | 동일 | `customEvent:repeat_days_bucket` |
| `time_window_bucket` | `routine_template_share_tapped`, `routine_template_share_sheet_opened` | #407 코드 계약 추가 / GA4 등록 필요 | 동일 | `customEvent:time_window_bucket` |
| `routine_name_included` | `routine_template_share_tapped`, `routine_template_share_sheet_opened` | #407 코드 계약 추가 / GA4 등록 필요 | 동일 | `customEvent:routine_name_included` |
| `period_type` | `lock_history_performance_summary_viewed`, `lock_history_top_apps_viewed` | #465 문서 계약 추가 / 코드 구현 전 | LockHistory 성과 리포트 포함 버전 배포 전후로 GA4 Admin 등록 후 metadata 확인 | `customEvent:period_type` |
| `report_state` | `lock_history_performance_summary_viewed` | #465 문서 계약 추가 / 코드 구현 전 | empty/low-data/has-history 상태별 summary 노출 비교. 실패/중독 상태값 금지 | `customEvent:report_state` |
| `session_count_bucket` | `lock_history_performance_summary_viewed` | #465 문서 계약 추가 / 코드 구현 전 | raw session 목록/개별 timestamp 없이 count bucket만 사용 | `customEvent:session_count_bucket` |
| `duration_minutes_bucket` | `lock_history_performance_summary_viewed` | #465 문서 계약 추가 / 코드 구현 전 | raw duration 값 대신 bucket만 사용 | `customEvent:duration_minutes_bucket` |
| `top_apps_count_bucket` | `lock_history_top_apps_viewed` | #465 문서 계약 추가 / 코드 구현 전 | top app 이름/package 원문 금지. 표시 개수 bucket만 사용 | `customEvent:top_apps_count_bucket` |
| `duration_selection_type` | `goal_lock_created` | #417 code-lane 생성 ViewModel/analytics 계약 추가, release/GA4 등록 전 | 목표 잠금 포함 release/tag/Play deploy 전후로 GA4 Admin 등록 후 metadata 확인 | `customEvent:duration_selection_type` |
| `lock_mode` | `goal_lock_created`, `goal_lock_completed`, `goal_lock_ended_early`, `goal_lock_updated` | `goal_lock_created` 코드 계약 추가, detail 종료 path의 early-end runtime call 추가, completion runtime call 추가 / release·GA4 등록 전 | 동일 | `customEvent:lock_mode` |
| `selected_app_count_bucket` | `goal_lock_created` | #417 code-lane 생성 ViewModel/analytics 계약 추가, release/GA4 등록 전 | 동일 | `customEvent:selected_app_count_bucket` |
| `goal_name_type` | `goal_lock_created` | #417 code-lane 생성 ViewModel/analytics 계약 추가, release/GA4 등록 전 | 목표 이름 원문을 보내지 않고 preset/custom enum만 확인 | `customEvent:goal_name_type` |
| `duration_days_bucket` | `goal_lock_completed` | #417 detail load에서 만료된 active 목표 잠금을 completed로 정규화하고 bucketed completion event emit / release·GA4 등록 전 | 동일 | `customEvent:duration_days_bucket` |
| `elapsed_days_bucket` | `goal_lock_ended_early` | #417 detail 종료 path의 early-end runtime call 추가 / release·GA4 등록 전 | 동일 | `customEvent:elapsed_days_bucket` |
| `surface` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | #455 문서 계약 추가 / 코드 구현 전 | 루틴 생성 CTA 포함 버전 배포 전후로 GA4 Admin 등록 후 metadata 확인 | `customEvent:surface` |
| `activation_stage` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | #455 문서 계약 추가 / 코드 구현 전 | 동일 | `customEvent:activation_stage` |
| `has_routine` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | #455 문서 계약 추가 / 코드 구현 전 | 루틴 보유자 오노출 감지. MVP는 `false`만 허용 | `customEvent:has_routine` |
| `allowed_app_count_bucket` | `parent_mode_allowed_apps_selected`, `parent_mode_started` | #471 문서 계약 추가 / 코드 구현 전 | 부모 모드 허용 앱 개수별 setup/시작 전환 비교. 아이 이름/앱 이름/package/raw session history 금지 | `customEvent:allowed_app_count_bucket` |
| `duration_minutes_bucket` | `parent_mode_duration_selected`, `parent_mode_started`, `parent_mode_completed` | #471 문서 계약 추가 / 코드 구현 전 | 부모 모드 시간 선택/완료 bucket. raw timestamp/duration 원문 대신 bucket만 사용 | `customEvent:duration_minutes_bucket` |
| `pin_result` | `parent_mode_unlocked_by_pin` | #471 문서 계약 추가 / 코드 구현 전 | 보호자 PIN 성공/실패 UX guardrail. PIN 원문/길이/세부값 금지 | `customEvent:pin_result` |
| `end_reason` | `parent_mode_completed`, `parent_mode_unlocked_by_pin`, `parent_mode_cancelled` | #471 문서 계약 추가 / 코드 구현 전 | 시간 만료/PIN 해제/취소/시스템 중단 종료 사유 분리. raw timestamp 금지 | `customEvent:end_reason` |
| `block_context` | `parent_mode_block_intercepted` | #471 문서 계약 추가 / 코드 구현 전 | 허용되지 않은 앱/설정/최근 앱/알림 surface 우회 리스크 분리 | `customEvent:block_context` |

### 2) Recommended 이벤트 차원

| 코드 파라미터 | 주 사용 이벤트 | 현재 상태 | 등록 시점 |
| --- | --- | --- | --- |
| `error` | `review_prompt_failed` | 2026-06-02T18:06:45Z 미등록 확인 (`customEvent:error` invalid dimension) | review 실패 원인 추적이 실제로 필요할 때 GA4 Admin 등록 |
| `blocking_mode` | `first_core_action_completed`, `core_action_completed` | 미확인 | 첫 가치 경험 비교를 appVersion별로 재분석할 때 |
| `routine_id` | `first_core_action_completed`, `core_action_completed` | 미확인 | 루틴별 성과/문제 추적이 필요할 때. 앱 내부 계약은 문자열이며 `block_source=routine`인 차단 경로에서만 non-null이다. |
| `goal_lock_id` | `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` | #417 code-lane 계약 추가 / 미확인 | 목표 잠금별 차단성과/문제 추적이 필요할 때. 앱 내부 계약은 문자열이며 `block_source=goal_lock`인 차단 경로에서만 non-null이다. 목표 이름/app label 원문은 등록·전송하지 않는다. |
| `screen_name` | `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` | 2026-06-01 metadata 등록 확인 | 광고 성과와 screen drift를 같이 볼 때 |
| `ad_currency` | `ad_banner_revenue` | 미확인 | 다통화/정산 검증이 필요할 때 |
| `ad_precision_type` | `ad_banner_revenue` | 미확인 | 추정 수익 vs 정밀 수익 구분이 필요할 때 |
| `interest_variant` | `monetization_interest_shown`, `monetization_interest_clicked` | 2026-06-03 코드 계약 추가 / 필요 시 등록 | CTA copy/variant 비교가 필요할 때 |
| `purchase_available` | `monetization_interest_shown`, `monetization_interest_clicked` | 2026-06-03 코드 계약 추가 / 필요 시 등록 | 결제 미구현 관심도 측정과 실제 구매 가능 상태를 분리할 때 |
| `cta_variant` | `routine_creation_cta_shown`, `routine_creation_cta_clicked`, `routine_creation_cta_dismissed` | #455 문서 계약 추가 / 필요 시 등록 | 루틴 생성 CTA copy/placement 비교가 필요할 때 |

### 3) Recommended 이벤트 지표

| 코드 파라미터 | 주 사용 이벤트 | 현재 상태 | 등록 시점 |
| --- | --- | --- | --- |
| `selected_app_count` | `app_selection_completed`, `first_lock_configured` | 미확인 | 앱 선택량 분포를 정량 비교할 때 |
| `scheduled_duration_minutes` | `lock_scheduled` | 미확인 | 타이머/루틴 시간 길이 분석이 필요할 때 |
| `duration_minutes` | `emergency_unlock_completed` | 미확인 | 긴급해제 사용 길이 분포를 볼 때 |
| `remaining_unlocks` | `emergency_unlock_completed` | 미확인 | 잔여 긴급해제 수 패턴을 볼 때 |
| `elapsed_since_first_open_seconds` | `first_core_action_completed`, `core_action_completed` | 미확인 | first value latency를 분석할 때 |
| `ad_value_micros` | `ad_banner_revenue` | 2026-06-01 metadata 등록 확인 | placement/context별 수익 분포를 재집계할 때 |

운영 원칙:

- `Required`가 비어 있으면 퍼널/광고/리뷰 해석 confidence를 낮춘다.
- `Recommended`는 꼭 모두 한 번에 등록할 필요는 없지만, 분석 질문이 생기면 문서보다 먼저 GA4 등록 상태를 확인한다.

## GA4 Admin 수동 등록 절차

> 실제 Admin UI 텍스트는 Google 측에서 바뀔 수 있다. 아래는 현재 운영 기준의 절차이며, 바뀌면 이 문서를 같이 수정한다.

### 이벤트 차원 등록

1. GA4 property `502544175`를 연다.
2. **Admin → Custom definitions → Create custom dimensions**로 이동한다.
3. 아래 값을 입력한다.
   - Dimension name: 코드 파라미터와 동일하거나 사람이 읽기 쉬운 이름
   - Scope: `Event`
   - Event parameter: 코드 파라미터명 그대로 입력 (`step_name`, `source`, `ad_placement` 등)
   - Description: 이벤트 목적을 한 줄로 기록
4. 저장 후 metadata query로 `customEvent:<parameter>`가 보이는지 확인한다.
5. 증적 표에 등록 일시/담당/metadata 확인 여부를 남긴다.

### 이벤트 지표 등록

1. **Admin → Custom definitions → Create custom metrics**로 이동한다.
2. 아래 값을 입력한다.
   - Metric name: 사람이 읽기 쉬운 이름
   - Event parameter: 코드 파라미터명 그대로 입력
   - Unit: 숫자/시간/통화 의미에 맞게 설정
3. 저장 후 metadata query로 `customEvent:<parameter>` metric이 보이는지 확인한다.
4. 증적 표에 등록 일시/담당/metadata 확인 여부를 남긴다.

## 등록 증적 템플릿

### registration ledger

| 항목 | 분류 | 등록 여부 | 등록 일시 | 담당 | metadata 확인 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
| `step_name` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `permission_name` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `outcome` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `source` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `block_source` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `blocked_app_package` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `selected_app_count` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `is_onboarding` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `is_routine` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `end_reason` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `reason` | Required dimension | 등록 확인 | 2026-06-02T18:06:45Z | docs lane | `customEvent:reason` | #307 `review_prompt_skipped` breakdown에서 조회 가능 확인. 다른 이벤트 coverage는 별도 확인 |
| `screen_context` | Required dimension | 등록 확인 | 2026-06-01 | docs lane | `customEvent:screen_context` | AdMob queryability preflight에서 확인. 남은 경계는 PR #293 `ad_banner_*` 포함 release/tag/Play deploy 후 14일 coverage/source-split 재조회 |
| `ad_placement` | Required dimension | 등록 확인 | 2026-06-01 | docs lane | `customEvent:ad_placement` | 동일 |
| `ad_format` | Required dimension | 등록 확인 | 2026-06-01 | docs lane | `customEvent:ad_format` | 동일 |
| `ad_unit_id` | Required dimension | 등록 확인 | 2026-06-01 | docs lane | `customEvent:ad_unit_id` | 동일 |
| `screen_name` | Recommended dimension | 등록 확인 | 2026-06-01 | docs lane | `customEvent:screen_name` | 광고 성과와 screen drift를 같이 볼 때 사용 |
| `ad_value_micros` | Recommended metric | 등록 확인 | 2026-06-01 | docs lane | `customEvent:ad_value_micros` | placement/context별 수익 분포 재집계용 |
| `interest_context` | Required dimension | 등록 필요 | CTA 포함 버전 배포 전후 | GA4 Admin 수동 | `customEvent:interest_context` 확인 필요 | PR #362 코드 계약 + 2026-06-04 메뉴/설정 CTA 연결 완료. 등록/metadata 확인 없이는 문맥별 클릭률 판단 금지 |
| `interest_surface` | Required dimension | 등록 필요 | CTA 포함 버전 배포 전후 | GA4 Admin 수동 | `customEvent:interest_surface` 확인 필요 | 안전한 surface별 관심 클릭률 판단의 필수 축 |
| `interest_variant` | Recommended dimension | 필요 시 등록 | CTA copy/variant 비교 전 | GA4 Admin 수동 | `customEvent:interest_variant` 확인 필요 | A/B가 없으면 `default` payload만 남기고, 비교 실험 전 등록 |
| `purchase_available` | Recommended dimension | 필요 시 등록 | 결제 가능 상태 분리 전 | GA4 Admin 수동 | `customEvent:purchase_available` 확인 필요 | 결제 미구현 관심도 측정(`false`)과 실제 구매 가능 상태를 분리할 때 등록 |
| `template_category` | Required dimension | 등록 필요 | 루틴 템플릿 공유 CTA 구현·배포 전후 | GA4 Admin 수동 | `customEvent:template_category` 확인 필요 | #407 Android share sheet MVP의 카테고리별 공유 의도 비교. 앱 목록/package/raw history 금지 |
| `repeat_days_bucket` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:repeat_days_bucket` 확인 필요 | 요일 패턴별 공유 의도 비교. enum/bucket만 허용 |
| `time_window_bucket` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:time_window_bucket` 확인 필요 | 시간대 패턴별 공유 의도 비교. raw time/session history 금지 |
| `routine_name_included` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:routine_name_included` 확인 필요 | 이름 원문 대신 opt-in 여부 boolean만 기록 |
| `surface` | Required dimension | 등록 필요 | 루틴 생성 CTA 구현·배포 전후 | GA4 Admin 수동 | `customEvent:surface` 확인 필요 | #455 soft CTA의 Home/History/post-block 표면별 반응 비교 |
| `activation_stage` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:activation_stage` 확인 필요 | post-first-core-action/returning blocked user 맥락 분리 |
| `has_routine` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:has_routine` 확인 필요 | 루틴 보유자 오노출 감지. MVP는 `false`만 허용 |
| `cta_variant` | Recommended dimension | 필요 시 등록 | CTA copy/placement 비교 전 | GA4 Admin 수동 | `customEvent:cta_variant` 확인 필요 | MVP는 `default` 단일 variant로 시작 |
| `allowed_app_count_bucket` | Required dimension | 등록 필요 | 부모 모드 구현·배포 전후 | GA4 Admin 수동 | `customEvent:allowed_app_count_bucket` 확인 필요 | #471 same-device 부모 모드의 허용 앱 개수별 setup/시작 전환 비교. 앱 이름/package 원문 금지 |
| `duration_minutes_bucket` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:duration_minutes_bucket` 확인 필요 | 부모 모드 시간 선택/완료 bucket. raw timestamp/duration 원문 대신 bucket만 사용 |
| `pin_result` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:pin_result` 확인 필요 | 보호자 PIN 성공/실패 UX guardrail. PIN 원문/길이/세부값 금지 |
| `block_context` | Required dimension | 등록 필요 | 동일 | GA4 Admin 수동 | `customEvent:block_context` 확인 필요 | 허용되지 않은 앱/설정/최근 앱/알림 surface 우회 리스크 분리 |
| `extension_minutes_bucket` | Recommended dimension | 필요 시 등록 | 부모 모드 연장 패턴 비교 전 | GA4 Admin 수동 | `customEvent:extension_minutes_bucket` 확인 필요 | 보호자 PIN 확인 후 연장 시간 bucket |

### metadata 확인 로그 템플릿

```md
- 확인 일시:
- 확인자:
- command/snippet:
- 새로 보인 customEvent:* 항목:
  -
- 아직 안 보이는 항목:
  -
- 해석:
```

## metadata / runReport 검증 절차

### 1. metadata 확인

`docs/ANALYTICS_EVENT_DICTIONARY.md`의 metadata query snippet을 사용한다.

판단 기준:

- 등록 직후 바로 안 보일 수 있으므로 즉시 1회, 이후 지연이 있으면 다시 재확인한다.
- `customEvent:*`가 metadata에 없으면 대시보드에서 아직 직접 조회 불가 상태로 본다.

### 2. runReport 조회성 확인

metadata에 보인 뒤에는 실제로 필요한 쿼리에서 dimension/metric을 써 본다.

판단 기준:

- `customEvent:*`를 넣은 `runReport`가 `400 INVALID_ARGUMENT`와 함께 `Field customEvent:... is not a valid dimension` 또는 유사 메시지를 반환하면, **데이터가 0인 것이 아니라 등록 자체가 아직 안 된 상태**로 본다.
- 이 경우 제품 결론을 내리기보다 registration ledger / Admin 등록 / metadata 재확인을 먼저 진행한다.

예시 확인 질문:

- `permission_name` / `outcome` 기준으로 권한 병목이 나눠지는가
- `source` 기준으로 `first_lock_configured`가 onboarding/home/routine으로 나뉘는가
- `ad_placement` / `screen_context` 기준으로 CTR/eCPM 비교가 가능한가

### 추천 확인 순서

1. **activation check**
   - `permission_outcome` by `permission_name`, `outcome`
   - `first_lock_configured` by `source`
   - `app_block_intercepted` by `block_source`, `blocked_app_package`
2. **trust/review check**
   - `lock_session_end` by `end_reason`, `is_routine`
   - `review_prompt_skipped` / `review_prompt_failed` by `reason` / `error`
3. **monetization check**
   - PR #293 포함 release/tag/Play deploy 이후: `ad_banner_impression`, `ad_banner_click`, `ad_banner_revenue` by `ad_placement`, `screen_context`, `ad_unit_id`
   - PR #293 이전 legacy `ad_impression`, `ad_click`, `ad_revenue` breakdown은 SDK 자동 이벤트와 앱 custom event가 섞였던 baseline으로만 본다.
   - `monetization_interest_clicked` / `monetization_interest_shown` by `interest_context`, `interest_surface`
4. **routine template share check**
   - #407 구현 포함 release/tag/Play deploy 이후: `routine_template_share_tapped` / `routine_template_share_sheet_opened` / `routine_template_share_failed` by `template_category`, `repeat_days_bucket`, `time_window_bucket`, `routine_name_included`
   - 앱 이름/package/lockApplications/raw session history를 query 축으로 찾으려는 분석은 privacy contract 위반으로 중단한다.
5. **goal lock check**
   - #417 구현 포함 release/tag/Play deploy 이후: `goal_lock_created` / `goal_lock_completed` / `goal_lock_ended_early` by `duration_selection_type`, `lock_mode`, `selected_app_count_bucket`, `goal_name_type`, `duration_days_bucket`, `elapsed_days_bucket`
   - 목표 이름 원문/app package/app label/raw 날짜를 query 축으로 찾으려는 분석은 privacy/trust contract 위반으로 중단한다.
6. **routine creation CTA check**
   - #455 구현 포함 release/tag/Play deploy 이후: `routine_creation_cta_shown` / `routine_creation_cta_clicked` / `routine_creation_cta_dismissed` by `surface`, `activation_stage`, `has_routine`, `cta_variant`
   - onboarding / pre-first-lock 사용자는 분모에서 제외하고, 앱 이름/package/lockApplications/raw session history를 query 축으로 찾으려는 분석은 privacy contract 위반으로 중단한다.
7. **parent mode check**
   - #471 구현 포함 release/tag/Play deploy 이후: `parent_mode_started` / `parent_mode_completed` / `parent_mode_unlocked_by_pin` / `parent_mode_block_intercepted` by `allowed_app_count_bucket`, `pin_result`, `end_reason`, `block_context`, `extension_minutes_bucket`
   - 아이 이름/앱 이름/package/raw session history, 허용 앱 원문 목록, PIN 원문/길이/세부값을 query 축으로 찾으려는 분석은 privacy/trust contract 위반으로 중단한다.

이 순서를 쓰면 docs lane / metrics lane / product lane이 모두 같은 우선순위로 follow-through를 해석할 수 있다.

### 2026-05-29 live queryability smoke 결과

- metadata 결과:
  - `customUser:*`: `routines_count`
  - `customEvent:*`: 없음
- activation smoke (`permission_outcome` by `customEvent:permission_name`, `customEvent:outcome`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:permission_name is not a valid dimension.`
- activation smoke (`first_lock_configured` by `customEvent:source`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:source is not a valid dimension.`
- trust/review smoke (`review_prompt_skipped` by `customEvent:reason`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:reason is not a valid dimension.`
- monetization smoke (`ad_*` by `customEvent:ad_placement`, `customEvent:screen_context`, `customEvent:ad_unit_id`):
  - `400 INVALID_ARGUMENT`
  - `Field customEvent:ad_placement is not a valid dimension.`

해석:

- 당시 문제는 "이벤트가 최근 14일에 0건이라 안 보이는가"가 아니라, **GA4 Admin 등록이 없어 쿼리 축 자체가 materialize되지 않은 상태**였다.
- 따라서 activation 세부 파라미터와 review `customEvent:error` 분석은 계속 낮은 confidence로 두고, `customEvent:*` 등록 전에는 분해 지표를 근거로 backlog 우선순위를 과신하지 않는다. review `customEvent:reason`은 2026-06-02T18:06:45Z #307 재조회에서 등록/조회 가능해졌으므로, skip reason breakdown에 사용할 수 있다. monetization/AdMob 축은 2026-06-01 보정 이후 단순 Admin 미등록이 아니라 PR #293 `ad_banner_*` 포함 release/tag/Play deploy 후 coverage/source-split 재측정 대기 상태로 분리한다.

### 2026-06-01 AdMob queryability preflight 보정 / PR #293 이후 경계

- 광고 관련 custom dimensions/metrics는 이후 metadata에 등록된 것으로 확인됐다.
  - `customEvent:ad_unit_id`
  - `customEvent:ad_placement`
  - `customEvent:screen_context`
  - `customEvent:ad_format`
  - `customEvent:ad_value_micros`
  - `customEvent:screen_name`
- 당시 legacy `ad_impression` / `ad_click` / `ad_revenue` breakdown은 `(not set)`/empty가 컸다.
- 이후 PR #293에서 Stopit 앱 소유 배너 이벤트가 `ad_banner_impression` / `ad_banner_click` / `ad_banner_revenue`로 분리됐다.
- 따라서 광고 쪽 다음 경계는 이 문서의 Admin registration ledger가 아니라 `docs/ADMOB_MONETIZATION_RUNBOOK.md`의 `GA4 query template: publisher surface와 Stopit 앱 custom 이벤트 분리` 및 `release boundary snapshot`에 따라 **PR #293 포함 commit이 release/tag/Play deploy에 실제 포함된 뒤 14일 재조회**를 실행하는 것이다. 2026-06-02 확인 기준 최신 production tag `v1.7.7`은 PR #293 split commit을 포함하지 않으므로, 아직 post-split measurement window는 시작되지 않았다.

주의: 이 보정은 광고 파라미터에 한정한다. 활성화(`permission_name`, `source` 등)와 리뷰 `error` 축은 별도 metadata/runReport 확인 전까지 계속 registration gap으로 취급한다. 리뷰 `reason` 축은 2026-06-02T18:06:45Z에 `customEvent:reason` metadata와 `review_prompt_skipped` breakdown이 확인됐으므로, #307 skip reason 판단에는 registration gap으로 반복 보고하지 않는다.

### 2026-06-03 screen quality live smoke: release boundary 확인

- 확인 시각: `2026-06-03 09:12 KST`
- 확인 명령: `/tmp/stopit_screen14_probe.py`에서 `properties/502544175:runReport`를 `14daysAgo..yesterday` 창으로 조회
- 확인된 최근 14일 screen quality:
  - total `screen_view`: `22,584`
  - `(not set)` `unifiedScreenName`: `11,793`
  - blank `unifiedScreenName`: `1,987`
  - combined gap: `13,780 / 22,584 = 61.0%`
- top visible screens: `BlockScreen` `5,030`, `HomeScreen` `1,735`, `OnboardingIntroScreen` `601`, `SplashScreen` `376`, `RoutineScreen` `361`, `MenuScreen` `259`, `LockScreen` `134`
- ancestry check:
  - PR #296 merge commit `47e43784c4111cc0a16bbc3a8872e51a28dcda0f` is in `origin/develop`, but not in `origin/main` or production tag `v1.7.7`.
  - PR #318 merge commit `8d2ee10beb0f235905008ca0fcdd314b2c599c24` is in `origin/develop`, but not in `origin/main` or production tag `v1.7.7`.

해석:

- 61.0% smoke는 2026-05-29 baseline 78.1%보다 좋아 보이지만, PR #296/#318 포함 code가 아직 `origin/main`/`v1.7.7` production boundary를 넘지 않았으므로 **post-fix 14일 성과로 승격하지 않는다**.
- 이 값은 현재 live 계정의 screen-name 상태를 과거 baseline과 같은 쿼리로 다시 찍은 **중간 smoke**다. #13 closure 판단은 PR #296/#318 포함 release/tag/Play deploy 후 14일 창에서 같은 분자/분모로 다시 채운다.
- 같은 화면(`SplashScreen`, `BlockedAppsScreen`, `EmergencyUnlockSettingsScreen`, `DevToolScreen`)에 대한 추가 code-lane 후보를 만들기 전에, 먼저 release inclusion과 14일 재측정 여부를 확인한다.

### 2026-06-04T20:14:53Z metrics snapshot readback: 30일 합산 guardrail

- 확인 명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
- window: `30daysAgo..yesterday`
- 확인된 30일 screen quality:
  - total `screen_view`: `36,707`
  - `(not set)` `unifiedScreenName`: `23,074`
  - `(not set)` share: `23,074 / 36,707 = 62.9%`
  - visible top screens: `BlockScreen` `5,856`, `HomeScreen` `3,252`, `RoutineScreen` `647`, `OnboardingIntroScreen` `677`, `SplashScreen` `645`, `MenuScreen` `299`, `LockScreen` `162`
- latest observed production version adoption smoke:
  - `appVersion=1.7.7` activeUsers: `107`
  - total activeUsers: `757`
  - latest-version active share: `107 / 757 = 14.1%` → `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 `주의`

해석:

- 이 30일 합산 readback은 위 14일 `13,780 / 22,584 = 61.0%` smoke를 대체하는 같은 쿼리 창이 아니다. 다만 최신 cron snapshot에서도 `(not set)` 비중이 여전히 높고 최신 production cohort active share가 아직 `30%` 미만이므로, #13을 post-fix 성과로 닫을 근거가 아니다.
- 다음 repo-internal 판단은 screen_view 보강 PR을 반복 생성하는 것이 아니라, PR #296/#318 포함 release/tag/Play deploy 여부와 D+14 같은 쿼리 창 재측정 여부를 먼저 확인하는 것이다.

### 3. 14일 재측정

등록/배포 후 14일 창에서 아래를 같이 본다.

- `(not set)` + 빈 `unifiedScreenName` 비율
- 새 `customEvent:*` 항목 metadata 유지 여부
- 실제 runReport에서 dimension/metric 조회 성공 여부
- activation / review / monetization 분석 confidence가 올라갔는지

## 14일 재측정 표

| 항목 | baseline (2026-05-29 기준) | +14일 | 해석 |
| --- | --- | --- | --- |
| `screen_view` 총량 | `13,154` | `TODO` | |
| `(not set)` `unifiedScreenName` | `9,473` | `TODO` | |
| 빈 `unifiedScreenName` | `801` | `TODO` | |
| `(not set)+빈 값` 비율 | `78.1%` | `TODO` | 2026-05-29 baseline은 PR #296의 `SplashScreen` / `BlockedAppsScreen` / `EmergencyUnlockSettingsScreen` 및 PR #318의 dev/debug `DevToolScreen` 보강 전 기준선. 2026-06-03 14일 live smoke는 `13,780 / 22,584 = 61.0%`였고, 2026-06-04 30일 cron snapshot은 `(not set)` `23,074 / 36,707 = 62.9%`였다. 두 값 모두 PR #296/#318이 `origin/main`/`v1.7.7`에 없고 최신 `1.7.7` active share도 `107 / 757 = 14.1%`라 post-fix 성과가 아니라 중간 smoke/주의 상태로만 기록한다. PR #296/#318 포함 버전 배포 후 14일 창에서 재측정. `DevToolScreen`은 production 사용자 지표와 분리 |
| metadata에서 보이는 `customUser:*` | `routines_count` | `TODO` | |
| activation metadata에서 보이는 `customEvent:*` | `없음` | `TODO` | `permission_name`, `outcome`, `source`, `selected_app_count`, `block_source`, `blocked_app_package` 등 activation/funnel breakdown은 아직 낮은 confidence |
| review skip metadata에서 보이는 `customEvent:*` | `reason` | `TODO` | 2026-06-02T18:06:45Z 기준 `review_prompt_skipped` reason breakdown 조회 가능. #307에서는 더 이상 `reason`을 미등록 경계로 반복 보고하지 않음 |
| review failure metadata에서 보이는 `customEvent:*` | `없음` (`error` 미등록) | `TODO` | `review_prompt_failed` 원인 breakdown은 계속 #13 GA4 Admin/manual boundary |
| 광고 metadata에서 보이는 `customEvent:*` | `ad_unit_id`, `ad_placement`, `screen_context`, `ad_format`, `ad_value_micros`, `screen_name` | `TODO` | source split/query contract 확인 필요 |
| activation 분석 confidence | `낮음` | `TODO` | |
| review 분석 confidence | `낮음` | `TODO` | |
| monetization 분석 confidence | `낮음` | `TODO` | 광고 metadata는 일부 복구됐고 PR #293에서 이벤트명 분리 완료. 단, 2026-06-02 기준 최신 production tag `v1.7.7`은 PR #293 split commit 미포함. PR #293 포함 release/tag/Play deploy 후 14일 재조회 전까지 placement별 결론 보류 |

## issue/PR handoff 템플릿

실제 GA4 Admin 등록과 재측정은 repo 밖 수동/라이브 작업이라, 저장소 문서만 바뀌고 handoff가 흐려지면 다시 같은 해석 혼선이 생긴다. 아래 형식을 issue #13 코멘트 또는 관련 PR 코멘트에 그대로 남기는 것을 기본값으로 둔다.

```md
## GA4 registration follow-through

- 확인 일시:
- 확인자:
- metadata 확인 결과:
  - customUser:*:
  - customEvent:*:
- activation check 결과:
  - `permission_outcome` by `permission_name/outcome`:
  - `first_lock_configured` by `source`:
  - `app_block_intercepted` by `block_source/blocked_app_package`:
- trust/review check 결과:
  - `lock_session_end` by `end_reason/is_routine`:
  - `review_prompt_skipped` / `review_prompt_failed`:
- monetization check 결과:
  - publisher surface (`publisherAdImpressions` / `publisherAdClicks` / `totalAdRevenue` by `adUnitName/adFormat`):
  - Stopit app custom coverage (`ad_banner_impression` / `ad_banner_click` / `ad_banner_revenue` by `ad_placement/screen_context/ad_unit_id`):
  - legacy `ad_impression` / `ad_click` / `ad_revenue`를 사용했다면 PR #293 이전 baseline인지 여부:
- routine template share check 결과:
  - `routine_template_share_tapped` / `routine_template_share_sheet_opened` / `routine_template_share_failed` by `template_category/repeat_days_bucket/time_window_bucket/routine_name_included`:
  - privacy guardrail 확인: 앱 이름/package/lockApplications/raw session history 축을 쓰지 않았는지:
- goal lock check 결과:
  - `goal_lock_created` / `goal_lock_completed` / `goal_lock_ended_early` by `duration_selection_type/lock_mode/selected_app_count_bucket/goal_name_type/duration_days_bucket/elapsed_days_bucket`:
  - privacy/trust guardrail 확인: 목표 이름 원문/app package/app label/raw 날짜 축을 쓰지 않았는지:
- routine creation CTA check 결과:
  - `routine_creation_cta_shown` / `routine_creation_cta_clicked` / `routine_creation_cta_dismissed` by `surface/activation_stage/has_routine/cta_variant`:
  - privacy guardrail 확인: 앱 이름/package/lockApplications/raw session history 축을 쓰지 않았는지:
- screen_view 품질:
  - total:
  - `(not set)`:
  - blank `unifiedScreenName`:
  - combined ratio:
- 해석:
- 남은 외부/manual 경계:
```

## docs lane 완료 범위 vs 외부 경계

### docs lane에서 완료 가능한 것

- 등록 대상 목록과 우선순위 문서화
- GA4 Admin 수동 절차 문서화
- metadata / runReport 검증 포맷 문서화
- 증적 ledger / 14일 재측정 표 준비
- 다른 docs/context-pack에 source of truth 링크 정리

### docs lane에서 완료할 수 없는 것

- 실제 GA4 Admin 등록 클릭 작업
- 등록 후 metadata에 항목이 나타나는지 live 확인
- 배포 후 실제 이벤트가 들어온 뒤 14일 재측정

따라서 docs lane PR은 기본적으로 `Refs #13`이 맞다. `Closes #13`는 아래가 모두 충족될 때만 가능하다.

- Required 차원/지표가 실제로 등록됨
- metadata / runReport 증적이 남음
- `(not set)` / 빈 screen name 비율이 개선됐는지 14일 재측정이 끝남
- 관련 code/docs/metrics 해석이 같은 계약으로 맞춰짐

## 운영 메모

- `docs/ANALYTICS_EVENT_DICTIONARY.md`는 **계약 정의서**, 이 문서는 **운영 등록 런북**으로 역할을 나눈다.
- 새로운 analytics 파라미터가 추가되면 두 문서를 같이 갱신한다.
- 향후 live 등록이 진행되면 이 문서의 ledger를 실제 값으로 채우고, issue #13 또는 PR 코멘트에 증적 URL/명령 출력을 연결한다.
