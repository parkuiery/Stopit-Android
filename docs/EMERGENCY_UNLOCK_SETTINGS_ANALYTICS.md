# Emergency Unlock Settings Analytics Contract

Issue: #694

## 목적

긴급해제 설정 화면은 사용자가 Stopit의 차단 강도와 안전장치를 직접 조절하는 고신뢰 표면이다. #694 code-lane에서 `EmergencyUnlockSettingsViewModel`의 설정 변경 경로에 Android analytics wiring을 연결했다. 이제 사용자가 실제로 바꾸는 정책(`enabled`, 일일 횟수, 허용 duration, reason required, daily/manual refill, manual reset)은 enum/bucket 이벤트로 남기되, GA4 Admin 등록·release/tag/Play deploy·14일/30일 readback 전까지 live event 부재를 adoption 부재로 해석하지 않는다.

이 문서는 #694의 source of truth다. 목표는 긴급해제 설정 adoption과 마찰을 **privacy-safe enum/bucket**으로만 해석할 수 있게 Android wiring 계약, GA4 등록 경계, QA evidence를 고정하는 것이다. 현재 repo-internal Android wiring은 완료됐지만 GA4 Admin·release/readback 전까지 `Refs #694`를 사용한다.

## 현재 구현 표면

- 화면: `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsScreen.kt`
- ViewModel: `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsViewModel.kt`
- 설정 저장소: `app/src/main/java/com/uiery/keep/datastore/EmergencyUnlockSettingsStore.kt`
- 정책/availability: `app/src/main/java/com/uiery/keep/service/EmergencyUnlockPolicy.kt`, `EmergencyUnlockCoordinator.kt`
- analytics 상수/API: `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt`, `FirebaseKeepAnalytics.kt`
- 현재 상태: `EmergencyUnlockSettingsViewModel.init`는 `screen_view(EmergencyUnlockSettingsScreen)`를 기록하고, 설정 변경/수동 reset 경로는 아래 `emergency_unlock_settings_changed` / `emergency_unlock_manual_reset_requested` 이벤트를 기록한다.

설정 변경 함수별 의미:

| 함수 | 사용자 의미 | 계측 필요성 |
| --- | --- | --- |
| `setEnabled(enabled)` | 긴급해제 기능 자체 ON/OFF | 기능 차단·불신·강한 자기통제 선호 신호 |
| `setDailyLimit(limit)` | 하루 사용 가능 횟수 변경 | 허용 횟수 adoption / 너무 엄격하거나 느슨한 기본값 검증 |
| `toggleDuration(minutes)` | 선택 가능한 해제 시간 option 변경 | 짧은/긴 해제 시간 선호와 마찰 확인 |
| `setReasonRequired(required)` | reason step 사용 여부 | reason 분포 confidence와 UX friction 분모 분리 |
| `setRefillMode(mode)` | daily refill vs manual refill | 강한 자기통제/manual reset adoption 확인 |
| `markManualReset()` | manual refill 선택 후 카운트 수동 회복 | manual mode 실제 사용·마찰 확인 |

## 이벤트 계약

### `emergency_unlock_settings_changed`

설정 값이 사용자의 명시적 조작으로 바뀌었을 때 기록한다. 설정 화면 진입만으로 기록하지 않는다.

| 파라미터 | 값 후보 | 설명 |
| --- | --- | --- |
| `setting_name` | `enabled`, `daily_limit`, `duration_options`, `reason_required`, `refill_mode` | 어떤 설정 축이 바뀌었는지 |
| `value_bucket` | 아래 setting별 bucket | 새 값 또는 상태를 privacy-safe bucket으로 표현 |
| `refill_mode` | `daily`, `manual`, `not_applicable` | refill 관련 변경이면 선택된 mode, 아니면 `not_applicable` |
| `duration_count_bucket` | `0`, `1`, `2_3`, `4_plus`, `not_applicable` | duration option 개수 변경일 때만 meaningful |
| `source` | `menu` | 현재 진입 surface. 후속 entrypoint가 생기면 enum으로만 확장 |

Setting별 `value_bucket`:

| `setting_name` | `value_bucket` 후보 |
| --- | --- |
| `enabled` | `on`, `off` |
| `daily_limit` | `1`, `2`, `3`, `4_plus` |
| `duration_options` | `none`, `short_only`, `mixed`, `long_included` |
| `reason_required` | `on`, `off` |
| `refill_mode` | `daily`, `manual` |

Duration option bucket 규칙:

- `none`: 선택 가능한 duration이 0개인 비정상/QA 실패 상태
- `short_only`: 모든 option이 5분 이하
- `mixed`: 5분 이하와 10분 이상이 섞임
- `long_included`: 15분 이상 option이 포함됨

### `emergency_unlock_manual_reset_requested`

Manual refill mode에서 사용자가 수동 회복을 요청했을 때 기록한다. 실제 reset 성공/실패까지 code-lane에서 분리할 수 있다면 `reset_result`를 추가하되 enum만 허용한다.

| 파라미터 | 값 후보 | 설명 |
| --- | --- | --- |
| `refill_mode` | `manual` | manual mode에서만 발생해야 한다 |
| `remaining_unlocks_bucket` | `0`, `1`, `2`, `3_plus`, `unknown` | reset 직전 남은 횟수 bucket. raw count가 아니라 bucket만 전송 |
| `source` | `menu` | 현재 설정 화면 진입 surface |
| `reset_result` | `requested`, `completed`, `unavailable` | optional. code-lane에서 결과 구분이 안전할 때만 추가 |

## 금지 payload / query 축

아래 값은 앱 코드 payload, GA4 custom dimension 등록, 문서 query template 모두에서 금지한다.

- custom reason 원문
- reason display label/custom text
- 앱 package name / 앱 label / 앱 이름 / selected app list / 앱 목록 / `lockApplications`
- raw lock/session history, raw lock history, raw session history, raw timestamp
- raw manual reset timestamp (`manualResetAtMillis` 원문)
- device/account/user identifier
- 설정 snapshot dump / 설정 변경 전후 전체 snapshot dump

## 해석 원칙

- `enabled=off` 증가는 제품 불신일 수도 있고 사용자가 의도한 강한 자기통제일 수도 있다. `emergency_unlock_completed` 감소만으로 좋고 나쁨을 단정하지 않는다.
- `refill_mode=manual` adoption은 강한 자기통제 모드 선호 신호지만, `manual_reset_requested`가 많고 리뷰/긴급해제 불만이 늘면 마찰 신호로 본다.
- `reason_required=off` 사용자가 늘면 `emergency_unlock_completed.reason` 분포 confidence를 낮추고, reason-required-on cohort와 전체 cohort를 분리한다.
- duration option을 줄이는 사용자는 더 엄격한 self-control cohort일 수 있으므로, 차단 반복/긴급해제 완료/리뷰 guardrail을 함께 본다.
- Android analytics wiring 완료 전 live 0건은 수요 없음으로 해석하지 않는다. 이 PR 이후에도 release/tag/Play deploy 전 live 0건은 수요 없음이 아니라 **미배포/미관측**이다.
- 구현 후에도 GA4 Admin 등록, release/tag/Play deploy, 14일/30일 readback 전에는 adoption 결론을 보류한다.

## GA4 등록 / readback 경계

`docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`에는 아래 customEvent 축을 추가해야 한다.

Required:

- `customEvent:setting_name`
- `customEvent:value_bucket`
- `customEvent:refill_mode`
- `customEvent:duration_count_bucket`
- `customEvent:remaining_unlocks_bucket`
- `customEvent:source`는 기존 `source` 축을 재사용한다.

Recommended / optional:

- `customEvent:reset_result` — code-lane에서 manual reset 결과까지 안전하게 구분할 때만 등록한다.

Readback 최소 쿼리:

1. 14일/30일 `emergency_unlock_settings_changed` users / `EmergencyUnlockSettingsScreen` users
2. `setting_name` × `value_bucket`별 변경 users/eventCount
3. `refill_mode=manual` users와 `emergency_unlock_manual_reset_requested` users 비교
4. `reason_required=off` cohort와 전체 `emergency_unlock_completed.reason` 분포 confidence 분리
5. guardrail: `emergency_unlock_completed` users / active blocked users, review/rating, crash-free users

## Code-lane implementation state

#694 code-lane wiring 상태:

- `KeepAnalytics` / `FirebaseKeepAnalytics`: `emergency_unlock_settings_changed`, `emergency_unlock_manual_reset_requested` 이벤트 API와 Firebase payload 구현 완료.
- `EmergencyUnlockSettingsViewModel`: `enabled`, `daily_limit`, `duration_options`, `reason_required`, `refill_mode`, `manual_reset` 변경 경로에서 `source=menu`와 privacy-safe enum/bucket payload만 기록.
- 테스트: `FirebaseKeepAnalyticsTest.emergencyUnlockSettingsEventsUsePrivacySafeBucketsOnly`와 `EmergencyUnlockSettingsViewModelAnalyticsTest`가 raw reason/app package/raw timestamp/manualResetAtMillis 없이 enum/bucket payload만 나가는지 고정.
- 남은 외부 경계: GA4 Admin custom dimension/metric 등록, SemVer tag/Play deploy 포함, D+14/D+30 readback.

## Historical code-lane handoff

TDD 순서 후보:

1. `FirebaseKeepAnalyticsTest`에 `emergency_unlock_settings_changed`와 `emergency_unlock_manual_reset_requested` payload가 enum/bucket만 포함하고 금지 payload가 없는지 RED를 추가한다.
2. `KeepAnalytics` API를 추가한다.
   - `trackEmergencyUnlockSettingsChanged(settingName, valueBucket, refillMode, durationCountBucket, source)`
   - `trackEmergencyUnlockManualResetRequested(remainingUnlocksBucket, source, resetResult?)`
3. `FirebaseKeepAnalytics`에서 이벤트명/파라미터 상수를 구현한다.
4. `EmergencyUnlockSettingsViewModel` setting mutation 함수에 analytics 호출을 연결한다.
5. `EmergencyUnlockSettingsViewModelAnalyticsTest`를 화면 진입 `screen_view`뿐 아니라 setting mutation event까지 확장한다.
6. `docs/ANALYTICS_EVENT_DICTIONARY.md`, `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`, `docs/QA_RUNTIME_CHECKLIST.md`를 코드 PR에서 구현 상태로 다시 동기화한다.

## QA evidence template

```md
## Emergency unlock settings analytics QA evidence
- Issue: #694
- Build / variant:
- Commit / PR:
- Settings entry path: Menu → Emergency unlock settings
- Verification commands:
  - `python3 -m unittest scripts.tests.test_emergency_unlock_settings_analytics_contract -v`
  - `./gradlew --console=plain :app:testDevDebugUnitTest --tests 'com.uiery.keep.analytics.FirebaseKeepAnalyticsTest' --tests 'com.uiery.keep.feature.emergencyunlocksettings.EmergencyUnlockSettingsViewModelAnalyticsTest'`
- Setting changes exercised:
  - enabled on/off:
  - daily limit:
  - duration options:
  - reason required:
  - refill mode daily/manual:
  - manual reset:
- Privacy check:
  - no custom reason text: pass / fail
  - no app package/name/list: pass / fail
  - no raw timestamp/history/manualResetAtMillis: pass / fail
- GA4 Admin state:
  - customEvent:setting_name registered: yes / no
  - customEvent:value_bucket registered: yes / no
  - customEvent:refill_mode registered: yes / no
  - customEvent:duration_count_bucket registered: yes / no
  - customEvent:remaining_unlocks_bucket registered: yes / no
- Release/readback boundary:
  - included in SemVer tag / Play deploy: yes / no
  - D+14 readback window started:
- Decision: pass / fail / needs follow-up
```

## 완료 기준 / 남은 경계

Repo-internal docs-lane 완료 기준:

- [x] #694 설정 변경 이벤트 이름/파라미터/bucket을 privacy-safe 계약으로 정의한다.
- [x] 이벤트 딕셔너리, GA4 등록 runbook, metrics/product/context 문서에서 source of truth를 링크한다.
- [x] static docs contract test가 source of truth, 금지 payload, GA4/readback 경계를 고정한다.

Repo-internal code-lane 완료 기준:

- [x] Android analytics API/Firebase adapter/ViewModel wiring이 구현됐다. (PR #698 / merge commit `8c303d75204bf9b2b6ab1e0ed4c9b6d8e2489260`)
- [x] raw reason/app/package/timestamp/`manualResetAtMillis`가 payload에 포함되지 않음을 JVM 테스트로 보장한다. (`FirebaseKeepAnalyticsTest.emergencyUnlockSettingsEventsUsePrivacySafeBucketsOnly`, `EmergencyUnlockSettingsViewModelAnalyticsTest`)

남은 external / release / measurement 경계:

- [ ] GA4 Admin custom dimension 등록과 metadata 확인을 수행한다.
- [ ] #694 포함 버전이 release/tag/Play deploy된다.
- [ ] 배포 후 14일 또는 30일 readback으로 setting adoption을 해석한다.
