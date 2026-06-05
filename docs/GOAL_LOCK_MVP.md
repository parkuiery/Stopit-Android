# 목표 잠금 MVP

Issue: #417

이 문서는 기간 기반 장기 앱 잠금 기능인 **목표 잠금**의 제품/analytics/QA/implementation handoff 계약을 고정한다. #417은 `ready` 상태지만, 코드 lane이 바로 들어가기 전에 “타이머/루틴을 조금 늘리는 기능”으로 축소되거나 “강력 제한 모드”까지 섞이지 않도록 MVP 범위와 외부 경계를 분리한다.

## 한 줄 목표

사용자가 7일·14일·30일 또는 종료 날짜까지 방해 앱을 **하루종일 또는 특정 시간대**로 잠그고, 홈에서 목표 진행 상태를 계속 확인할 수 있게 한다.

## 제품 의도

대표 사용 사례:

- 30일 동안 유튜브/인스타그램/틱톡 같은 방해 앱을 하루종일 잠그고 싶다.
- 시험일까지 매일 저녁 7시~11시에 게임 앱을 잠그고 싶다.
- 프로젝트 마감일까지 SNS를 줄이고 싶다.

핵심은 “시험 준비”라는 좁은 vertical이 아니라, 기간과 목표 이름이 있는 장기 자기통제 약속이다. 따라서 앱 내 가칭은 `목표 잠금`으로 둔다.

## MVP 범위

### 포함

1. 목표 잠금 생성 플로우
   - 목표 이름: preset(`시험 준비`, `SNS 줄이기`, `게임 줄이기`, `수면 습관`) + 직접 입력.
   - 기간 설정: preset days(`7`, `14`, `30`), custom days, end date.
   - 잠금 방식: `하루종일 잠금` / `특정 시간만 잠금`.
   - 앱 선택: 기존 앱 선택 UI/도메인 로직 재사용.
2. 하루종일 잠금과 시간대 잠금 모두 지원
   - `all_day`: 기간 내 매일 선택 앱을 종일 차단.
   - `scheduled`: 기간 내 선택 요일/시간대에만 차단.
3. 홈 진행 카드/섹션
   - 목표 이름.
   - 남은 기간 또는 종료일.
   - 잠금 방식.
   - 선택 앱 수.
   - 진행 중/완료/종료됨 상태.
   - 상세/설정 화면 진입 CTA.
4. 종료일 이후 자동 완료/비활성화
   - 종료일이 지나면 더 이상 차단하지 않는다.
   - 홈/상세 화면에서 완료 상태를 비난 없이 보여준다.
5. 수정/종료 확인 UX
   - MVP 기본값은 사용자가 종료할 수 있게 둔다.
   - 수정/종료는 한 번 더 확인한다.
   - 문구는 “약속을 지키도록 돕는다”는 톤이지, 사용자의 선택권을 빼앗거나 혼내는 톤이 아니다.
6. 기본 analytics 계약
   - 목표 잠금 생성 시작/완료.
   - 기간 선택 방식.
   - 잠금 방식.
   - 앱 수 bucket.
   - 조기 종료 여부.

### 제외

- 강력 목표 잠금 / 변경·해제 제한.
- 긴급 해제 횟수 자동 회복 OFF.
- 기간 중 설정 변경 완전 제한.
- 친구/부모/외부 인증.
- 과목별 시간표.
- 성공 리포트/공유 카드.
- 프리미엄/결제 연결.

후속 `강력 목표 잠금`은 #414 계열 긴급해제 강력 제한 모드와 연결할 수 있지만, #417 MVP에는 넣지 않는다.

## 상태/도메인 계약

### 추천 모델 필드

구현 이름은 code lane에서 확정하되, 정책 테스트는 아래 개념을 보존해야 한다.

| 필드 | 의미 | 주의 |
| --- | --- | --- |
| `goalLockId` | 목표 잠금 식별자 | analytics에는 raw id를 보내지 않는다. |
| `goalName` | 사용자 목표 이름 또는 preset label | analytics에는 원문 대신 `goal_name_type`만 보낸다. |
| `startDate` | 시작 날짜 | timezone / local date 기준을 명확히 한다. |
| `endDate` | 종료 날짜 | 종료일 다음 순간부터 자동 완료. |
| `durationSelectionType` | `preset_days`, `custom_days`, `end_date` | custom 입력 원문 전송 금지. |
| `lockMode` | `all_day`, `scheduled` | all-day와 scheduled 모두 MVP 포함. |
| `repeatDays` | scheduled 잠금 요일 | all-day는 기간 내 매일로 해석. |
| `timeWindow` | scheduled 잠금 시간대 | overnight 경계 테스트 필요. |
| `selectedAppCount` | 선택 앱 수 | raw app/package는 analytics 금지. |
| `status` | `active`, `completed`, `ended_early` | 종료일/조기 종료를 구분. |

### 차단 판단 정책

정책 helper는 Android framework 없이 JVM 테스트 가능한 순수 함수로 먼저 만든다.

필수 케이스:

- 기간 전이면 차단하지 않는다.
- 기간 내 `all_day`는 선택 앱을 하루종일 차단한다.
- 기간 내 `scheduled`는 선택 요일/시간대에만 차단한다.
- overnight 시간대는 시작일/다음날 경계를 명확히 처리한다.
- 종료일이 지나면 자동 완료/비활성화되어 차단하지 않는다.
- 선택 앱이 0개면 생성 완료 analytics를 기록하지 않거나 validation 실패로 처리한다.

## UX / 카피 계약

### 생성 진입

- 홈 또는 루틴/타이머 주변의 보조 CTA로 시작한다.
- 핵심 차단/긴급해제/권한 설정 같은 민감 flow 중간에 강하게 끼워 넣지 않는다.
- 최초 MVP에서는 “시험 준비 모드” 단독 명칭보다 “목표 잠금”을 canonical로 쓴다.

### 종료/수정 확인 문구 원칙

좋은 톤:

> 목표 잠금을 끝내면 오늘부터 선택한 앱이 다시 열릴 수 있어요. 지금 종료할까요?

피해야 할 톤:

- “의지가 부족합니다”
- “실패했습니다”
- “정말 포기하겠습니까?”
- 사용자의 선택권을 빼앗거나 벌주는 듯한 표현

## Analytics 계약 초안

구현 PR은 `KeepAnalytics.kt`, Firebase 구현, 테스트, `docs/ANALYTICS_EVENT_DICTIONARY.md`, GA4 등록 런북을 함께 업데이트한다.

| 이벤트 | 트리거 | 파라미터 | 민감 정보 정책 |
| --- | --- | --- | --- |
| `goal_lock_create_started` | 생성 플로우 진입 | `entry_surface` | 앱 이름/package/목표명 원문 금지 |
| `goal_lock_created` | 유효한 목표 잠금 저장 완료 | `duration_selection_type`, `lock_mode`, `selected_app_count_bucket`, `goal_name_type` | enum/bucket만 허용 |
| `goal_lock_completed` | 종료일 경과 후 자동 완료 상태 처리 | `lock_mode`, `duration_days_bucket` | raw 날짜/앱 목록 금지 |
| `goal_lock_ended_early` | 사용자가 기간 전 종료 | `lock_mode`, `elapsed_days_bucket`, `reason?` | reason은 enum만 허용 |
| `goal_lock_updated` | 기간/앱/시간대 수정 저장 | `lock_mode`, `changed_field` | changed_field는 enum만 허용 |

권장 enum/bucket:

- `entry_surface`: `home`, `routine`, `menu`, `goal_lock_detail`
- `duration_selection_type`: `preset_days`, `custom_days`, `end_date`
- `lock_mode`: `all_day`, `scheduled`
- `selected_app_count_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `goal_name_type`: `preset_exam`, `preset_sns`, `preset_game`, `preset_sleep`, `custom`
- `duration_days_bucket`: `1_6`, `7`, `8_14`, `15_30`, `31_plus`
- `elapsed_days_bucket`: `0`, `1_2`, `3_6`, `7_14`, `15_plus`
- `goal_lock_ended_early.reason`: `user_confirmed`, `validation_reset`, `unknown`
- `goal_lock_updated.changed_field`: `duration`, `apps`, `schedule`, `name`, `lock_mode`

GA4 custom dimension 등록은 구현/배포와 별개인 manual boundary다. event dictionary가 갱신돼도 Admin 등록과 metadata readback 전에는 `lock_mode`/기간별 전환율을 high-confidence로 해석하지 않는다.

## 측정 계획

### 14일 확인

기간: 목표 잠금 포함 버전 배포 후 14일 vs 배포 전 동기간.

기본 분자/분모:

- `goal_lock_created` users / active users.
- `goal_lock_created` users / `goal_lock_create_started` users.
- `goal_lock_completed` users / `goal_lock_created` users.
- `goal_lock_ended_early` users / `goal_lock_created` users.
- `app_block_intercepted` users among goal-lock users / `goal_lock_created` users.

Guardrail:

- crash-free users.
- Accessibility permission off / 권한 이탈.
- emergency unlock usage.
- review rating/review tone.
- 기존 루틴/타이머 사용률이 급락하지 않는지.

### 30일 확인

- D7/D30 목표 잠금 유지/완료율.
- all-day vs scheduled 선택 비율과 조기 종료율 비교.
- 목표 잠금 사용자 cohort의 `app_block_intercepted` repeat usage.
- 루틴 생성/타이머 사용과 cannibalization 여부.
- 리뷰/문의에서 “너무 강압적” 또는 “해제 못함” 톤이 생겼는지.

## QA / 테스트 체크리스트

### JVM 정책/계측/상태 테스트

- `GoalLockPolicyTest`:
  - 기간 전/기간 내/기간 후 상태 판정.
  - all-day 차단 판단.
  - scheduled 요일/시간대 차단 판단.
  - overnight window.
  - 종료일 이후 자동 completed.
  - selected app count 0 validation.
- `FirebaseKeepAnalyticsTest.goalLockCreatedUsesSafeBucketedParamsOnly`:
  - `goal_lock_created` 이벤트명/파라미터 enum/bucket 계약.
  - 목표 이름 원문, app package, app label이 payload에 들어가지 않음.
- `GoalLockPersistenceMapperTest`:
  - `GoalLockEntity` ↔ `GoalLock` round-trip.
  - `all_day` / `scheduled`, 날짜 문자열, 반복 요일/시간대, 선택 앱 목록, `active` / `ended_early` 저장 계약.
- `KeepDatabaseMigrationTest`:
  - v4→v5 migration에서 기존 `emergency_unlock` 데이터 보존.
  - 빈 `goal_lock` 테이블 생성.
- `GoalLockCreationViewModelTest`:
  - 유효한 all-day/scheduled 목표 잠금 저장.
  - invalid date/app/name selection 거절.
  - 목표별 선택 앱 편집에서 picker selection replace, package trim/dedupe, remove와 0개 validation을 검증.
  - `Created(goalLockId)` side effect.
  - `goal_lock_created` bucket-only analytics 호출.
- `HomeViewModelActivationAnalyticsTest`:
  - active/pending/ended_early 목표 잠금이 Home card state로 노출됨.
  - 종료일이 지난 active 목표 잠금을 Home card load 경로에서 `completed`로 정규화하고 `goal_lock_completed`를 1회 기록함.
- `GoalLockDetailViewModelTest`:
  - 상세 화면 상태가 목표 이름/잠금 방식/선택 앱 수를 노출함.
  - 종료 요청/취소가 확인 상태만 바꿈.
  - 사용자 확인 종료가 `ended_early`로 저장되고 `goal_lock_ended_early`를 enum/bucket만으로 기록함.
- `FirebaseKeepAnalyticsTest.goalLockEndedEarlyUsesSafeBucketedParamsOnly`:
  - `goal_lock_ended_early` 이벤트가 `lock_mode`, `elapsed_days_bucket`, `reason`만 기록함.

### ViewModel/UI state 테스트

- 생성 플로우 preset/custom/end date 선택.
- 홈 카드 active/completed/ended_early 상태.
- 상세/설정 CTA navigation.
- 수정/종료 확인 dialog state.

### Android/수동 QA

- all-day 목표 잠금이 하루 경계에서 계속 차단한다.
- scheduled 목표 잠금이 지정 시간대 밖에서는 차단하지 않는다.
- 종료일 경과 후 선택 앱이 다시 열릴 수 있다.
- 조기 종료 확인 문구가 비난/강압 톤이 아니다.
- TalkBack에서 홈 목표 잠금 카드가 목표 이름/남은 기간/상태를 이해 가능하게 읽는다.

## 구현 패키지 추천 범위

#417 구현 착수 시에는 아래를 한 PR 또는 명확한 code-lane package로 끝까지 묶는다.

1. 순수 `GoalLockPolicy` / model 추가와 JVM RED/GREEN.
2. 저장소/DAO 또는 기존 루틴/타이머 모델과의 persistence 경계 정의.
3. 생성 ViewModel/UI state와 validation.
4. Home card/section 노출.
5. Accessibility/blocking runtime이 목표 잠금 상태를 실제 차단 판단에 반영.
6. analytics 이벤트/테스트/event dictionary/GA4 ledger 동기화.
7. QA runtime checklist에 all-day/scheduled/expiration evidence 추가.

### 2026-06-04 QA foothold

QA lane에서 첫 repo-internal 자동화 foothold로 `app/src/main/java/com/uiery/keep/feature/goallock/GoalLockPolicy.kt`와 `app/src/test/java/com/uiery/keep/feature/goallock/GoalLockPolicyTest.kt`를 추가했다. 현재 고정된 범위는 기간 전/기간 내/기간 후 상태, `all_day`, `scheduled`, overnight window, 종료일 이후 자동 completed, selected app count 0 validation이다.

이 foothold는 실제 저장소/DAO, 생성 UI, Home card, Accessibility/blocking runtime 연결, analytics 구현을 대체하지 않는다. 다음 구현 package는 이 정책 API를 source of truth로 삼되, 남은 항목을 계속 같은 #417 acceptance 안에서 전진시킨다.

`Closes #417`는 위 구현+테스트+홈 노출+analytics+QA 문서까지 완료했을 때만 사용한다. 이 정책 foothold PR은 `Refs #417`가 맞다.

### 2026-06-04 persistence foothold

Code lane에서 다음 repo-internal foothold로 Room `goal_lock` 테이블(version 5), `GoalLockDao`, `GoalLockEntity` ↔ `GoalLock` mapper, schema export, `MIGRATION_4_5` migration contract를 추가했다. 현재 고정된 범위는 `all_day`/`scheduled` 저장 형식, 기간 날짜 문자열, 반복 요일/시간대, 선택 앱 목록, `active`/`ended_early` 상태 round-trip과 v4→v5 migration에서 기존 emergency-unlock 데이터를 보존하면서 빈 `goal_lock` 테이블을 생성하는 것이다.

이 foothold도 생성 UI, Home card, Accessibility/blocking runtime wiring, completed/ended analytics runtime wiring을 대체하지 않는다. 다음 구현 package는 저장소/DAO를 기준으로 생성 ViewModel/UI state와 Home/runtime 연결을 계속 전진시킨다.

### 2026-06-04 creation ViewModel foothold

Code lane에서 다음 repo-internal foothold로 `GoalLockCreationViewModel`을 추가했다. 현재 고정된 범위는 유효한 `all_day` / `scheduled` 목표 잠금을 `GoalLockDao.insert()`로 저장하고, invalid date/app/name selection은 저장·계측 없이 거절하며, 성공 시 `Created(goalLockId)` side effect와 `goal_lock_created` bucket-only analytics를 발생시키는 것이다.

이 foothold는 생성 상태/저장/계측 계약을 고정하지만, 실제 생성 UI/navigation entrypoint, 상세/설정 CTA navigation, Accessibility/blocking runtime wiring, 종료일 경과 completed persistence/analytics는 아직 대체하지 않는다.

### 2026-06-04 Home card / runtime blocking foothold

Code lane에서 다음 repo-internal foothold로 `HomeViewModel`이 `GoalLockDao.fetchAll()`을 구독하고 첫 active/pending/ended_early 목표 잠금을 `HomeGoalLockCardState`로 노출하도록 연결했다. 홈 UI에는 목표명, 남은 일수, 잠금 방식, 선택 앱 수를 보여주는 진행 카드가 추가됐다. 이어서 Accessibility/blocking runtime도 `GoalLockDao.fetchAll()` 캐시를 구독하고 `GoalLockPolicy.isBlocking(...)` 결과를 `block_source=goal_lock` / `goal_lock_id`와 함께 차단 판단에 반영하도록 연결했다.

### 2026-06-05 detail / early-end foothold

Code lane에서 홈 목표 잠금 카드를 상세 화면으로 연결하고, `GoalLockDetailViewModel` / `GoalLockDetailScreen` / `GoalLockDetailRoute`를 추가했다. 현재 고정된 범위는 목표 이름·잠금 방식·선택 앱 수 상세 상태, 비난하지 않는 조기 종료 확인, 사용자 확인 시 `ended_early` 저장, `goal_lock_ended_early` bucket-only analytics 호출이다.

이 foothold는 상세/종료 CTA와 early-end analytics runtime call을 고정하지만, 목표 잠금 생성 UI entrypoint, 수정 UX, 종료일 경과 시 completed 상태 persistence/analytics, 실제 device/emulator runtime QA evidence, GA4 Admin 등록, release/tag/Play deploy, 14/30일 측정은 아직 대체하지 않는다. 따라서 관련 PR은 `Refs #417`로 유지하고, 위 UI/runtime/analytics/release 경계까지 완료된 뒤에만 `Closes #417`를 사용한다.

### 2026-06-05 creation UI / navigation foothold

Code lane에서 같은 #417 package를 이어서 `GoalLockCreationRoute`, `GoalLockCreationScreen`, Menu의 `목표 잠금` entrypoint, 그리고 생성 성공 후 `GoalLockDetailRoute`로 이동하는 navigation을 추가했다. 생성 화면은 현재 홈의 앱 선택 상태를 `BlockingStateStore`에서 읽어 seed로 사용하고, 목표별 선택 앱 편집(홈 선택 재불러오기, `CategoryBottomSheetContent` 기반 full picker UX, picker selection replace, 개별 제거, 0개 validation), 목표 이름 preset/직접 입력, 7/14/30일 preset 기간, 직접 일수(`custom_days`) 입력, ISO 종료 날짜(`end_date`) 입력, `all_day`와 평일 저녁 `scheduled` 선택, 생성 가능 validation, `goal_lock_created` 호출 후 상세 화면 진입을 고정한다.

이 foothold는 생성 UI/navigation runway와 custom days/end date 기간 선택, 목표별 선택 앱 편집을 실제 앱 entrypoint 및 기존 앱 선택 picker 재사용까지 연결하지만, 종료일 경과 시 completed 상태 persistence/analytics, 실제 device/emulator runtime QA evidence, GA4 Admin 등록, release/tag/Play deploy, 14/30일 측정은 아직 대체하지 않는다. 따라서 관련 PR은 계속 `Refs #417`로 유지한다.

## 외부/manual 경계

- GA4 Admin custom dimension 등록과 metadata readback.
- 목표 잠금 포함 버전의 release/tag/Play deploy.
- 배포 후 14일/30일 측정.
- 강력 제한 모드나 결제/프리미엄 연결 여부는 대표님 제품 판단이 필요하다.

## 회귀 방지

문서 lane이 #417을 다시 만질 때 이 테스트가 깨지면 목표 잠금 계약/링크가 drift된 것이다.

```bash
python3 -m unittest scripts.tests.test_goal_lock_contract -v
```

이 regression은 goal-lock contract regression으로, MVP runbook·analytics dictionary·metrics/product context·QA checklist가 같은 source of truth를 보도록 고정한다.

## 연관 이슈

- #414: 긴급 해제 횟수 자동 회복 OFF / 강력 제한 모드. #417 MVP에서는 제외하고 후속 강력 목표 잠금에서 연결한다.
- #13: GA4 queryability / custom dimension 등록 경계.
- #14: 첫 잠금 활성화 퍼널. 목표 잠금은 첫 가치 이후 장기 유지/반복 사용 축으로 해석한다.
- #407: 루틴 템플릿 공유. 목표 잠금 성공 리포트/공유는 MVP 제외이며 privacy-safe 공유가 필요하면 별도 계약을 만든다.
