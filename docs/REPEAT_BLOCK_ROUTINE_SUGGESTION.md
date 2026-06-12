# 반복 차단 패턴 기반 자동 루틴 제안 계약

Issue: #531
상태: **code-lane policy + analytics foothold + 루틴 prefill 진입 계약 + dismiss local store + Home·LockHistory CTA UI wiring 구현 / release·GA4 등록·수동 런타임 QA 전**

이 문서는 최근 차단 기록에서 반복되는 시간대·요일·앱 카테고리 신호를 로컬에서 해석해, 사용자가 덜 힘들게 같은 약속을 지키도록 루틴 생성을 제안하는 기능의 source of truth다. 목적은 “또 실패했다”가 아니라 “이미 막아낸 패턴을 자동화해 다음에는 덜 흔들리게 돕는다”는 코칭 경험을 만드는 것이다.

이 문서/PR은 repo 내부 계약을 정리하는 docs-lane work에서 시작했고, 2026-06-06 code-lane에서 로컬 후보 산출 policy(`RepeatBlockRoutineSuggestionPolicy`)와 `repeat_block_routine_suggestion_*` analytics adapter 계약까지 전진했다. 이후 code-lane 후속 PR은 추천 후보를 type-safe `RoutineRoute`로 전달해 `RoutineBottomSheetViewModel`이 시간대/요일/대상 앱 후보를 사전 입력하고, 사용자가 이름을 직접 확인·수정한 뒤 저장할 때 `repeat_block_routine_suggestion_applied`를 남기는 prefill 진입 계약을 추가했다. QA-lane PR은 같은 추천을 닫았을 때 `time_bucket/day_type/category_bucket/dismissedAt`만 로컬 DataStore에 저장·복원하는 `RepeatBlockRoutineSuggestionStore`를 추가해 7일 재노출 제한 policy input을 실제 persist 가능한 형태로 고정했다. 이번 code-lane PR은 Home과 LockHistory 표면에서 추천 카드를 실제로 노출하고, apply는 루틴 prefill navigation으로, dismiss는 privacy-safe store와 dismissed analytics로 연결한다. 이 store와 UI는 raw app name/package/list/history/timestamp를 저장·전송하지 않는다. 아직 성과 리포트/post-block success 표면, release/tag/Play deploy, GA4 Admin 등록/metadata 확인, 수동 device/locale/TalkBack QA, 14일/30일 readback 경계가 남았으므로 현재 PR은 `Refs #531`을 사용한다. issue closing keyword는 위 경계까지 acceptance가 실제로 충족될 때만 쓴다.

## 근거 / 연결 맥락

- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md` 기준 루틴 보유자는 루틴 미보유자보다 sessions / activeUsers와 `app_block_intercepted` users / activeUsers가 높다.
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`(#455)는 “첫 차단 성공 이후 + 루틴 0개 사용자”에게 루틴 생성 CTA를 부드럽게 제안하는 일반 soft CTA다.
- #531은 #455보다 한 단계 뒤의 개인화 후보로, 실제 반복 차단 패턴이 관측된 사용자에게 **하나의 구체 추천 후보**를 제시한다.
- `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`(#465)는 반복 차단/성과 리포트 표면과 맞닿아 있지만, #531은 리포트 전체가 아니라 루틴 생성 자동화 제안으로 범위를 제한한다.
- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`(#407)는 루틴을 이미 만든 사용자의 privacy-safe 공유 루프다. #531은 루틴 생성 전환/자동화 추천이며 공유 payload나 deep link/import와 섞지 않는다.
- `docs/GOAL_LOCK_MVP.md`(#417)의 목표 잠금과 동시에 후보가 생기면 현재 active protection/goal-lock 안내가 자동 루틴 제안보다 우선한다.

## MVP 대상 / 제외 조건

### 대상

- 최근 7일 또는 14일 안에 같은 시간대/요일 유형/앱 카테고리에서 반복 차단이 관측된 사용자
- 해당 패턴을 이미 커버하는 활성 루틴이 없는 사용자
- 첫 핵심 행동(`first_core_action_completed`) 또는 실제 차단(`app_block_intercepted`)을 이미 경험한 사용자
- Home, post-block success, LockHistory/성과 리포트처럼 사용자가 이미 방어 가치를 확인한 표면

### 제외

- onboarding / pre-first-lock 사용자는 제외
- 차단 기록이 부족하거나 최근 기록이 없는 사용자는 제외
- 이미 같은 시간대·요일·대상 앱을 커버하는 루틴이 있으면 제외
- 사용자가 같은 추천을 닫은 경우 최소 7일간 재노출하지 않는다
- active goal lock / emergency unlock / 강한 보호 상태가 현재 화면의 주요 맥락이면 자동 루틴 제안을 뒤로 미룬다
- Usage Access 권한을 새 필수 전제로 요구하지 않는다. MVP는 기존 LockHistory/차단 기록만 사용한다.

## 로컬 반복 패턴 분석 계약

MVP는 외부 추론 서비스나 Usage Access 추가 권한 없이 로컬 기록만 사용한다.

### 입력 후보

- `LockHistory` / 차단 이력의 blocked app set 또는 block source
- 루틴 목록(Room source of truth)과 현재 활성/비활성 상태
- 차단 시각을 privacy-safe bucket으로 변환한 값
- 같은 앱/카테고리의 짧은 시간 재시도 여부

### 패턴 키

외부 이벤트에 원문을 보내지 않는 전제로, 로컬 후보 산출에는 아래 수준의 키만 사용한다.

- `time_bucket`: `morning`, `afternoon`, `evening`, `night`, `overnight`
- `day_type`: `weekday`, `weekend`, `daily`, `custom_days`
- `category_bucket`: `social`, `video`, `game`, `shopping`, `browser`, `unknown`
- `routine_coverage_state`: `not_covered`, `partially_covered`, `covered`
- `repeat_count_bucket`: `3_5`, `6_10`, `10_plus`

앱 package/name 원문은 로컬 매칭에는 필요할 수 있지만, analytics payload / 공유 payload / PR 문서 evidence에는 원문을 남기지 않는다.

### MVP 임계치 초안

- 최근 7일 내 같은 `time_bucket × category_bucket`에서 차단 3회 이상
- 또는 최근 14일 동안 같은 `day_type × time_bucket` 조합이 2주 연속 반복
- 차단 후 5분 이내 같은 category/app 재시도가 2회 이상이면 강한 반복 신호로 가중
- 후보가 여러 개면 최근성 → 반복 횟수 bucket → 루틴 미커버 상태 순으로 하나만 고른다
- `routine_coverage_state=covered`는 추천하지 않는다. `partially_covered`는 첫 MVP에서는 보류하고, 충돌 없는 확장으로만 다룬다.

## UX / copy 원칙

- 코칭 톤: “막아냈어요”, “덜 힘들게 만들 수 있어요”, “자동으로 도와드릴게요”를 사용한다.
- 금지 톤: 실패, 중독, 과사용, 감시, 처벌, 부끄러움.
- 추천은 최대 1개만 노출한다.
- 추천 CTA는 사용자가 수정 가능한 루틴 생성 플로우로 연결한다.
- prefill은 시간대/요일/대상 앱 후보를 제안하되, 저장 전 사용자가 직접 확인·수정해야 한다.
- 같은 화면에서 #455 일반 루틴 CTA, #407 공유 CTA, 광고 CTA와 slot 충돌이 나면 사용자 압박이 낮은 순서로 조정한다.

권장 문구 예시:

- “최근 밤 시간대에 SNS 차단을 여러 번 지켜냈어요. 수면 전 루틴으로 덜 힘들게 이어갈까요?”
- “평일 저녁에 영상 앱 차단이 반복돼요. 이 시간대만 자동 루틴으로 설정해둘 수 있어요.”
- “주말 오후 게임 앱 차단이 자주 보여요. 주말 전용 루틴 후보를 만들어볼까요?”

## Analytics 계약

신규 이벤트 후보:

| 이벤트명 | 필수 파라미터 | 의미 |
| --- | --- | --- |
| `repeat_block_routine_suggestion_shown` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 반복 차단 기반 루틴 추천 노출 |
| `repeat_block_routine_suggestion_clicked` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 추천 CTA 클릭 후 루틴 생성 prefill 흐름 진입 |
| `repeat_block_routine_suggestion_dismissed` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 사용자가 추천을 닫거나 나중에 보기 선택 |
| `repeat_block_routine_suggestion_applied` | `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant` | 추천 prefill에서 루틴 저장 완료 |

파라미터 enum:

- `surface`: `home`, `post_block_success`, `lock_history`, `performance_report`
- `suggestion_reason`: `repeat_block_time_bucket`, `repeat_block_day_time`, `rapid_retry`
- `time_bucket`: `morning`, `afternoon`, `evening`, `night`, `overnight`
- `day_type`: `weekday`, `weekend`, `daily`, `custom_days`
- `category_bucket`: `social`, `video`, `game`, `shopping`, `browser`, `unknown`
- `repeat_count_bucket`: `3_5`, `6_10`, `10_plus`
- `routine_coverage_state`: `not_covered`, `partially_covered`; `covered`는 추천 노출 실패로 간주한다
- `suggestion_variant`: `default`

Privacy guardrail:

- 앱 이름, package name, `lockApplications`, raw session history, raw timestamp, raw retry count, raw routine name은 이벤트 payload나 query 축에 넣지 않는다.
- `routine_id`는 추천 shown/clicked/dismissed payload에 넣지 않는다. 저장 완료 후 기존 루틴/차단 이벤트 계약에서만 다룬다.
- `category_bucket=unknown`이 과도하면 앱 카테고리 매핑 정확도 문제로 보되, raw package를 GA4로 보내 해결하지 않는다.

## 측정 기준

### Primary

- 추천 클릭률: `repeat_block_routine_suggestion_clicked` users / `repeat_block_routine_suggestion_shown` users
- 추천 적용률: `repeat_block_routine_suggestion_applied` users / `repeat_block_routine_suggestion_clicked` users
- 추천 저장 완료율: `routine_saved(creation_source=repeat_block_prefill)` users / `repeat_block_routine_suggestion_clicked` users, 단 `entry_surface=repeat_block_suggestion|home|lock_history|performance_report`로 제한
- 추천 cohort의 루틴 보유 전환: 추천 노출 users 중 `routines_count >= 1` users / suggestion shown users

`routine_saved`는 #810 generic 루틴 저장 완료 이벤트다. #531의 `repeat_block_routine_suggestion_applied`는 추천 prefill이 저장 완료까지 이어졌다는 추천-specific 이벤트로 유지하고, #810 구현 후에는 같은 저장 성공에서 generic `routine_saved`도 함께 남겨 수동/CTA/추천 저장 완료 분모를 비교한다. Android wiring·GA4 Admin·release/tag/Play deploy 전에는 `routine_saved` 0건을 추천 실패로 해석하지 않고, 기존 `repeat_block_routine_suggestion_applied`와 `routines_count >= 1` 전환을 보조 지표로 유지한다.

### Secondary

- 추천 cohort의 7일 내 `app_block_intercepted` users / suggestion shown users
- 추천 적용 후 `lock_session_start(source=routine)` 또는 루틴 기반 `app_block_intercepted` users / applied users
- LockHistory / Home repeat visit users

### Guardrail

- `repeat_block_routine_suggestion_dismissed` users / shown users
- `emergency_unlock_completed` users / active blocked users
- crash-free users
- Play Console rating/review
- #455 일반 루틴 CTA, #407 루틴 템플릿 공유 CTA, 광고 CTA의 전환 악화 여부

## GA4 / release 경계

- `repeat_block_routine_suggestion_*` 이벤트가 코드에 추가되어도 GA4 Admin에서 `surface`, `suggestion_reason`, `time_bucket`, `day_type`, `category_bucket`, `repeat_count_bucket`, `routine_coverage_state`, `suggestion_variant`가 custom dimension으로 등록되고 metadata에서 확인되기 전에는 breakdown confidence를 낮춘다.
- #810 `routine_saved` Android wiring이 추가되어도 GA4 Admin에서 `entry_surface`, `creation_source`, `selected_app_count_bucket`, `repeat_days_bucket`, `time_window_bucket`, `schedule_state`가 등록되고 metadata에서 확인되기 전에는 추천 click → 저장 완료 breakdown confidence를 낮춘다.
- 추천 및 #810 routine_saved 포함 commit이 `origin/main`, SemVer tag, Play deploy에 포함되기 전의 live 0건은 수요 없음이 아니라 release-boundary 전 상태로 본다.
- 최신 버전 active share가 `docs/VERSION_ADOPTION_METRICS_GATE.md` 기준 10% 미만이면 `보류`, 10~30%면 `주의`, 30% 이상이면 `충분`으로 표시한다.
- 14일 체크는 추천 포함 버전의 배포/GA4 Admin 등록/metadata 확인이 끝난 뒤 시작한다.
- 30일 체크는 이벤트 의미와 추천 임계치가 같은 window에서만 비교한다.

## 구현 handoff checklist

- [x] 반복 차단 패턴 계산 helper를 pure policy로 분리하고 JVM test를 추가한다. (`RepeatBlockRoutineSuggestionPolicyTest`)
- [x] 기존 활성 루틴과 겹치는 추천을 노출하지 않는다.
- [x] 후보가 여러 개여도 최대 1개만 노출한다.
- [x] 추천 dismiss는 privacy-safe bucket + `dismissedAt`만 로컬 DataStore에 저장·복원한다. (`RepeatBlockRoutineSuggestionStoreTest`; UI wiring 전)
- [x] 추천 dismiss/apply store를 Home/LockHistory CTA UI에 연결해 실제 재노출 제한을 화면 플로우에서 검증한다. (`HomeViewModelActivationAnalyticsTest`, `LockHistoryViewModelShareTest`; device/TalkBack 수동 QA 전)
- [x] 루틴 생성 prefill은 저장 전 사용자가 수정 가능하다. (`RoutineBottomSheetViewModel` prefill 계약)
- [x] analytics는 enum/bucket/boolean만 전송하고 raw 앱 이름/package/history/timestamp를 금지한다. (`repeat_block_routine_suggestion_*` adapter/test)
- [x] 한국어/영어 등 지원 locale copy가 비난형이 아닌 방어 성공/도움 제안 톤이다. (Home/LockHistory CTA string resource 추가; 실제 device locale/TalkBack QA 전)
- [ ] 차단 기록 없음/부족, 루틴 이미 존재, 추천 닫힘, active goal lock/emergency unlock 상태 QA가 있다. (policy/Home/LockHistory JVM 일부 완료, UI/runtime QA 전)
- [ ] 배포 후 14일/30일 측정 표와 guardrail 판정 기준이 PR/issue에 남는다.

## QA evidence template

```md
## Repeat block routine suggestion QA evidence

- 빌드/버전:
- 테스트 사용자 상태:
  - first_core_action_completed 여부:
  - routines_count / 로컬 루틴 수:
  - 최근 LockHistory 기간:
- 반복 패턴 후보:
  - time_bucket:
  - day_type:
  - category_bucket:
  - repeat_count_bucket:
  - routine_coverage_state:
- 기대 결과:
  - 추천 노출/미노출:
  - CTA surface:
  - prefill 수정 가능 여부:
  - 동일 추천 dismiss 후 재노출 제한:
- analytics 확인:
  - `repeat_block_routine_suggestion_shown`:
  - `repeat_block_routine_suggestion_clicked`:
  - `repeat_block_routine_suggestion_applied`:
  - raw app name/package/history/timestamp absent:
- guardrail:
  - emergency unlock 상태와 충돌 없음:
  - goal lock active 상태와 충돌 없음:
  - #455/#407/광고 CTA slot 압박 없음:
```

## 연결 문서

- `docs/ROUTINE_RETENTION_COHORT_BASELINE.md`: 루틴 보유/미보유 반복 사용 기준선
- `docs/ROUTINE_CREATION_CTA_EXPERIMENT.md`: #455 post-first-core-action 루틴 생성 CTA 계약
- `docs/ROUTINE_TEMPLATE_SHARE_MVP.md`: #407 루틴 템플릿 공유 루프와 slot 충돌 방지
- `docs/LOCK_HISTORY_PERFORMANCE_REPORT_MVP.md`: #465 반복 차단/성과 리포트 표면
- `docs/GOAL_LOCK_MVP.md`: #417 active protection 우선순위
- `docs/ANALYTICS_EVENT_DICTIONARY.md`: 이벤트/파라미터 계약
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: GA4 Admin 등록/metadata 확인 경계
- `docs/PRODUCT_METRICS_DASHBOARD.md`: 성장/반복 사용 지표 위치
- `docs/QA_RUNTIME_CHECKLIST.md`: 구현 PR 수동/자동 QA evidence

## 검증 명령

- `./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest' --tests 'com.uiery.keep.feature.lockhistory.LockHistoryViewModelShareTest'`
- `./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStoreTest'`
- `python3 -m unittest scripts.tests.test_repeat_block_routine_suggestion_contract -v`
- `git diff --check`

Refs #531
