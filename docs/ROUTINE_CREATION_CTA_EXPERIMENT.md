# 루틴 생성 CTA 실험 계약

Issue: #455
상태: **Home CTA·navigation·analytics 구현 완료 / GA4 Admin·release·14일·30일 readback 전**

이 문서는 첫 차단 성공을 이미 경험했지만 아직 루틴이 없는 사용자에게 루틴 생성 흐름을 부드럽게 제안하는 `post-first-core-action` CTA 실험의 source of truth다. 목적은 루틴 보유자의 반복 사용 신호를 제품 실험으로 연결하되, onboarding/첫 가치 경험/광고/공유 CTA를 방해하지 않는 것이다.

## 근거

`docs/ROUTINE_RETENTION_COHORT_BASELINE.md`의 2026-06-03 live readback 기준으로 루틴 보유자는 루틴 미보유자보다 반복 사용과 실제 차단 가치 신호가 강하다.

- 루틴 보유자(`routines_count >= 1`) sessions / activeUsers: `2,152 / 150 = 14.35`
- 루틴 미보유자(`routines_count = 0`) sessions / activeUsers: `1,180 / 155 = 7.61`
- 루틴 보유자 `app_block_intercepted` users / activeUsers: `91 / 150 = 60.7%`
- 루틴 미보유자 `app_block_intercepted` users / activeUsers: `62 / 155 = 40.0%`

단, `routines_count = (not set)` activeUsers가 560명으로 가장 크다. 따라서 이 문서는 루틴 상태가 확인된 사용자 중 **루틴이 0개이면서 이미 첫 핵심 행동을 완료한 사용자**에 대한 작은 soft CTA 실험 계약만 고정한다.

## 대상 / 제외 조건

### 대상

- `first_core_action_completed` 또는 `app_block_intercepted`를 이미 경험한 returning user
- 현재 루틴이 0개인 사용자(`routines_count = 0` 또는 앱 로컬 루틴 목록 0개)
- 첫 차단 성공 이후 Home, LockHistory, 또는 차단 완료 후속 안내처럼 사용자가 이미 핵심 가치를 경험한 surface

### 제외

- onboarding / pre-first-lock 사용자는 제외
- `first_lock_configured`만 있고 실제 차단이 아직 없는 사용자는 제외
- 루틴이 1개 이상 있는 사용자(`routines_count >= 1`)는 제외
- `routines_count = (not set)` 사용자는 user property만 보고 대상에 넣지 않는다. 로컬 루틴 목록 0개 + 첫 핵심 행동 완료 같은 앱 내부 상태가 같이 확인될 때만 후보로 본다.

## MVP UI 원칙

- CTA는 blocking modal이 아니라 **soft CTA**다.
- 카피는 “반복되는 시간에 자동으로 차단해볼까요?”처럼 도움/자동화 중심으로 쓴다.
- 수치심, 감시, 처벌, 실패/중독 뉘앙스를 쓰지 않는다.
- Usage Access 권한 요구와 섞지 않는다.
- 광고 배너 또는 수익화 CTA와 같은 화면에서 압박감이 생기면 루틴 CTA가 뒤로 물러난다.
- Routine empty state와 역할을 나눈다.
  - Routine empty state: 사용자가 루틴 탭/루틴 화면에 들어왔을 때 기본 설명
  - post-first-core-action CTA: 실제 차단 가치를 경험한 뒤 Home/History 등에서 반복 자동화를 제안
- #407 루틴 템플릿 공유 CTA는 루틴을 이미 만든 사용자의 성장 루프다. #455 CTA는 루틴을 아직 만들지 않은 사용자의 루틴 생성 전환 실험이므로 두 CTA를 같은 우선순위 slot에서 경쟁시키지 않는다.

## Analytics 계약

구현된 이벤트:

| 이벤트명 | 필수 파라미터 | 의미 |
| --- | --- | --- |
| `routine_creation_cta_shown` | `surface`, `activation_stage`, `has_routine`, `cta_variant` | 루틴 생성 CTA 노출 |
| `routine_creation_cta_clicked` | `surface`, `activation_stage`, `has_routine`, `cta_variant` | CTA 클릭 후 루틴 생성 흐름 이동 시도 |
| `routine_creation_cta_dismissed` | `surface`, `activation_stage`, `has_routine`, `cta_variant` | 명시 닫기/나중에 보기 등 사용자의 비전환 |

파라미터 enum:

- `surface`: `home_secondary`(현재 Home 보조 CTA 구현값), `lock_history`, `post_block_success`
- `activation_stage`: `post_first_core_action`, `returning_blocked_user`
- `has_routine`: `false`만 MVP에서 허용한다. 루틴 보유자에게 보이면 QA 실패다.
- `cta_variant`: `soft_default`부터 시작한다. copy/placement 실험 전에는 다중 variant로 해석하지 않는다.

Privacy guardrail:

- 앱 이름, package name, `lockApplications`, raw session history, raw lock timestamp를 이벤트 payload나 query 축에 넣지 않는다.
- `routines_count` user property는 최신 상태 스냅샷이다. CTA의 직접 원인/세션 attribution으로 과해석하지 않는다.
- `routine_id`는 루틴 생성 완료 후 기존 루틴/차단 이벤트 계약에서만 다룬다. CTA shown/clicked/dismissed에는 넣지 않는다.

## 측정 기준

### Primary

- CTA 클릭률: `routine_creation_cta_clicked` users / `routine_creation_cta_shown` users (`routine_creation_cta_clicked users / routine_creation_cta_shown users`)
- 루틴 저장 완료 전환: `routine_saved(creation_source=post_first_block_cta)` users / `routine_creation_cta_clicked` users (`routine_saved users / routine_creation_cta_clicked users`, filtered by `creation_source=post_first_block_cta`)
- 루틴 보유 전환: 실험 노출 cohort 중 `routines_count >= 1` users / `routine_creation_cta_shown` users

`routine_saved`는 #810에서 정의한 generic 루틴 저장 완료 이벤트 계약이다. Android wiring 전에는 아직 live code event가 아니므로, 현재 #455의 루틴 생성 전환은 CTA click 이후 `routines_count >= 1` user property 전환과 Routine 화면/저장 흐름 evidence를 함께 낮은 confidence로 본다. Android wiring 전에는 `routines_count >= 1` 전환을 보조 지표로 유지한다. #810 구현 후에는 `creation_source=post_first_block_cta`, `entry_surface=home_secondary|lock_history|post_block_success`, `schedule_state`를 함께 보되, GA4 Admin 등록·release/tag/Play deploy·14일/30일 readback 전까지는 event 0건을 CTA 실패로 해석하지 않는다.

### Secondary

- `app_block_intercepted` users / active users
- `app_block_intercepted` eventCount / blocked users
- D1/D7 return 또는 7일 내 2회 이상 차단 사용자 비율

### Guardrail

- `emergency_unlock_completed` users / active blocked users (`emergency_unlock_completed users / active blocked users`)
- Crashlytics crash-free users
- Play Console rating/review
- CTA dismiss rate
- onboarding/first-lock activation 지표 악화 여부

## 14일/30일 readback

- 14일 체크: CTA 포함 버전이 Play production/internal 측정 대상에 포함되고 최신 버전 active share가 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 최소 `주의` 이상일 때 시작한다.
- 30일 체크: 동일 이벤트/파라미터 의미가 유지된 30일 window에서 본다.
- 비교표에는 항상 분자/분모를 같이 남긴다.
- 최신 버전 active share가 10% 미만이면 `보류`, 10~30%면 `주의`, 30% 이상이면 `충분`으로 표시한다.

## GA4 / release 경계

- `routine_creation_cta_*` 이벤트가 코드/문서에 추가되어도 GA4 Admin에서 `surface`, `activation_stage`, `has_routine`, `cta_variant`가 custom dimension으로 등록되고 metadata에서 확인되기 전에는 세부 breakdown confidence를 낮춘다.
- #810 `routine_saved` Android wiring이 추가되어도 GA4 Admin에서 `entry_surface`, `creation_source`, `selected_app_count_bucket`, `repeat_days_bucket`, `time_window_bucket`, `schedule_state`가 등록되고 metadata에서 확인되기 전에는 CTA click → 저장 완료 breakdown confidence를 낮춘다.
- CTA 및 #810 routine_saved 포함 commit이 `origin/main`, SemVer tag, Play deploy에 포함되기 전의 live 0건은 수요 없음이 아니라 release-boundary 전 상태로 본다.
- PR #533 / merge commit `b7cf06f20aaf551f513e0684142577149b1c4550`로 Home 보조 CTA UI, Routine 화면 이동, dismiss/루틴 보유 숨김, analytics adapter/ViewModel 테스트가 `develop`에 반영됐다.
- 따라서 repo-internal CTA 구현은 다시 “구현 전”으로 되돌리지 않는다. 다만 GA4 Admin 등록, CTA 포함 release/tag/Play deploy, 14일·30일 측정 표가 남아 있으면 `Refs #455`를 사용한다. issue closing keyword는 GA4/release/14일·30일 readback까지 acceptance가 실제로 충족될 때만 쓴다.

## 구현 handoff checklist

- [x] 루틴 0개 + 첫 핵심 행동 완료 이후 사용자에게만 CTA가 노출된다. (`HomeStatusCtaReadModelTest`, `HomeViewModelActivationAnalyticsTest`)
- [x] onboarding / pre-first-lock 사용자에게는 CTA가 노출되지 않는다. (`hasTrackedFirstCoreAction=false` state/read-model 경계)
- [x] CTA 클릭 시 Routine 생성 흐름으로 이동한다. (`HomeSideEffect.MoveToRoutine` → `navigateToRoutine`)
- [x] CTA 이벤트가 privacy-safe enum 파라미터만 전송한다. (`RoutineCreationCtaAnalyticsTest`)
- [x] 루틴 생성 완료 후 `routines_count` 갱신 계약과 충돌하지 않는다. (로컬 `routineDao.observeAll()` count가 1 이상이면 CTA 숨김)
- [x] Routine empty state / 광고 배너 / #407 루틴 템플릿 공유 CTA와 UI 우선순위가 충돌하지 않는다. (Home 보조 CTA slot `home_secondary`)
- [x] ViewModel/state 테스트와 analytics adapter 테스트 또는 동등한 검증이 추가된다.
- [ ] 배포 후 14일/30일 측정 표와 guardrail 판정 기준이 PR/issue에 남는다.

## 연결 문서

- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`: 루틴 보유/미보유 반복 사용 기준선
- `docs/ANALYTICS_EVENT_DICTIONARY.md`: 이벤트/파라미터 계약
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: GA4 Admin 등록/metadata 확인 경계
- `docs/PRODUCT_METRICS_DASHBOARD.md`: input metric / guardrail 위치
- `docs/QA_RUNTIME_CHECKLIST.md`: 구현 PR 수동/자동 QA evidence
