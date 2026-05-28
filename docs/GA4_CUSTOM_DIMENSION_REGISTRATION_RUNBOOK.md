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

- `customUser:routines_count`만 metadata에서 확인됨
- `customEvent:*` 차원/지표는 아직 보이지 않음
- 최근 14일 `screen_view` 총량 `13,154`
- `(not set)` `9,473` + 빈 `unifiedScreenName` `801` = `10,274 / 13,154 = 78.1%`

해석:

- 현재 #13은 단순 문서 부재 문제가 아니라 **GA4 Admin 등록과 배포 후 계측 품질 회복이 아직 끝나지 않은 상태**다.
- 따라서 퍼널/광고/리뷰 결론은 계속 낮은 confidence로 다뤄야 한다.

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
| `reason` | `emergency_unlock_completed`, `device_registration_failed`, `device_registration_skipped`, `review_prompt_skipped` | 미확인/등록 필요 | 동일 | `customEvent:reason` |
| `screen_context` | `ad_impression`, `ad_click`, `ad_revenue` | 미확인/등록 필요 | 동일 | `customEvent:screen_context` |
| `ad_placement` | `ad_impression`, `ad_click`, `ad_revenue` | 미확인/등록 필요 | 동일 | `customEvent:ad_placement` |
| `ad_format` | `ad_impression`, `ad_click`, `ad_revenue` | 미확인/등록 필요 | 동일 | `customEvent:ad_format` |
| `ad_unit_id` | `ad_impression`, `ad_click`, `ad_revenue` | 미확인/등록 필요 | 동일 | `customEvent:ad_unit_id` |

### 2) Recommended 이벤트 차원

| 코드 파라미터 | 주 사용 이벤트 | 현재 상태 | 등록 시점 |
| --- | --- | --- | --- |
| `error` | `review_prompt_failed` | 미확인 | review 실패 원인 추적이 실제로 필요할 때 |
| `blocking_mode` | `first_core_action_completed`, `core_action_completed` | 미확인 | 첫 가치 경험 비교를 appVersion별로 재분석할 때 |
| `routine_id` | `first_core_action_completed`, `core_action_completed` | 미확인 | 루틴별 성과/문제 추적이 필요할 때 |
| `screen_name` | `ad_impression`, `ad_click`, `ad_revenue` | 미확인 | 광고 성과와 screen drift를 같이 볼 때 |
| `ad_currency` | `ad_revenue` | 미확인 | 다통화/정산 검증이 필요할 때 |
| `ad_precision_type` | `ad_revenue` | 미확인 | 추정 수익 vs 정밀 수익 구분이 필요할 때 |

### 3) Recommended 이벤트 지표

| 코드 파라미터 | 주 사용 이벤트 | 현재 상태 | 등록 시점 |
| --- | --- | --- | --- |
| `selected_app_count` | `app_selection_completed`, `first_lock_configured` | 미확인 | 앱 선택량 분포를 정량 비교할 때 |
| `scheduled_duration_minutes` | `lock_scheduled` | 미확인 | 타이머/루틴 시간 길이 분석이 필요할 때 |
| `duration_minutes` | `emergency_unlock_completed` | 미확인 | 긴급해제 사용 길이 분포를 볼 때 |
| `remaining_unlocks` | `emergency_unlock_completed` | 미확인 | 잔여 긴급해제 수 패턴을 볼 때 |
| `elapsed_since_first_open_seconds` | `first_core_action_completed`, `core_action_completed` | 미확인 | first value latency를 분석할 때 |
| `ad_value_micros` | `ad_revenue` | 미확인 | placement/context별 수익 분포를 재집계할 때 |

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
| `reason` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `screen_context` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `ad_placement` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `ad_format` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |
| `ad_unit_id` | Required dimension | `TODO` | `TODO` | `TODO` | `TODO` | |

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
   - `ad_impression`, `ad_click`, `ad_revenue` by `ad_placement`, `screen_context`, `ad_unit_id`

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

- 현재 문제는 "이벤트가 최근 14일에 0건이라 안 보이는가"가 아니라, **GA4 Admin 등록이 없어 쿼리 축 자체가 materialize되지 않은 상태**다.
- 따라서 activation / review / monetization 세부 파라미터 분석은 계속 낮은 confidence로 두고, `customEvent:*` 등록 전에는 분해 지표를 근거로 backlog 우선순위를 과신하지 않는다.

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
| `(not set)+빈 값` 비율 | `78.1%` | `TODO` | |
| metadata에서 보이는 `customUser:*` | `routines_count` | `TODO` | |
| metadata에서 보이는 `customEvent:*` | `없음` | `TODO` | |
| activation 분석 confidence | `낮음` | `TODO` | |
| review 분석 confidence | `낮음` | `TODO` | |
| monetization 분석 confidence | `낮음` | `TODO` | |

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
  - `ad_impression` / `ad_click` / `ad_revenue` by `ad_placement/screen_context/ad_unit_id`:
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
