# Emergency Unlock Step Analytics Contract

Issue: #779

## 목적

긴급해제 플로우는 신뢰/안전 민감 흐름이다. 기존 `emergency_unlock_used`와 `emergency_unlock_completed`만으로는 사용자가 reason → app selection → duration → countdown 중 어느 단계에서 멈추는지, 어떤 검증 실패가 실제 병목인지 알기 어렵다.

이 문서는 #779의 source of truth다. PR #781은 Android 구현 전 계약을 먼저 고정했고, PR #783(`12c47108`)은 Android `KeepAnalytics` / `FirebaseKeepAnalytics` API, `EmergencyUnlockBottomSheetContent` 단계/검증/취소 wiring, Block/Lock entry surface 연결, privacy-safe payload 테스트를 `develop`에 반영했다. 이제 repo-internal Android wiring은 완료 상태이며, 남은 경계는 GA4 Admin custom dimension 등록, release/tag/Play deploy 포함, 배포 후 D+14/D+30 readback이다. 후속 repo-internal acceptance가 추가되지 않는 한 이 이슈는 `Refs #779` 상태로 외부/측정 경계를 추적한다.

## 현재 기준선

현재 살아 있는 긴급해제 이벤트:

| 이벤트 | 의미 | 현재 해석 |
| --- | --- | --- |
| `emergency_unlock_used` | 긴급해제 플로우 진입 | 진입량은 보이지만 어느 단계에서 이탈했는지는 알 수 없다. |
| `emergency_unlock_completed` | countdown 완료 후 실제 임시 해제 완료 | 완료 reason/duration/remaining은 보이지만 검증 실패·취소·단계별 마찰은 알 수 없다. |
| `emergency_unlock_settings_changed` / `emergency_unlock_manual_reset_requested` | 긴급해제 설정 변경/수동 reset | #694 설정 분석 이벤트다. 단계별 이탈/검증 실패와 섞지 않는다. |

관련 source of truth:

- `docs/EMERGENCY_UNLOCK_FLOW_COPY.md` (#467): reason/app/duration/countdown UX copy와 단계 목적 계약.
- `docs/EMERGENCY_UNLOCK_SETTINGS_ANALYTICS.md` (#694): 설정 변경 analytics 계약.
- `docs/ANALYTICS_EVENT_DICTIONARY.md`: 이벤트/파라미터 dictionary.
- `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`: GA4 Admin 등록과 live metadata/readback boundary.

## 이벤트 계약

### `emergency_unlock_step_viewed`

사용자가 긴급해제 bottom sheet의 한 단계를 실제로 보았을 때 기록한다.

| 파라미터 | 타입 | 허용값 | 설명 |
| --- | --- | --- | --- |
| `step_name` | enum | `reason`, `app_selection`, `duration`, `countdown` | 표시된 단계. reason-required OFF에서는 `reason`이 기록되지 않아야 한다. |
| `reason_required_enabled` | boolean/string enum | `true`, `false` | reason step이 설정상 켜져 있었는지. reason 분포 confidence 분모를 분리한다. |
| `entry_surface` | enum | `lock_screen`, `block_screen`, `unknown` | 긴급해제 진입 표면. raw route/path는 금지한다. |

기록 원칙:

- 같은 bottom sheet session에서 같은 `step_name`은 중복 기록하지 않는 것을 권장한다.
- 뒤로/앞으로 이동으로 같은 step이 다시 렌더링돼도 session-local dedupe를 우선한다.
- 앱 list, 앱 package, 앱 label, custom reason text, raw timestamp는 포함하지 않는다.

### `emergency_unlock_validation_blocked`

사용자가 다음 단계로 진행하려 했지만 현재 단계의 필수 선택/입력이 부족해 막혔을 때 기록한다.

| 파라미터 | 타입 | 허용값 | 설명 |
| --- | --- | --- | --- |
| `step_name` | enum | `reason`, `app_selection`, `duration` | 검증 실패가 발생한 단계. countdown은 validation 단계가 아니므로 제외한다. |
| `validation_reason` | enum | `missing_reason`, `missing_custom_reason`, `missing_app_selection`, `missing_duration`, `duration_options_unavailable`, `unknown` | 사용자 입력 원문이 아니라 실패 종류만 남긴다. |
| `reason_required_enabled` | boolean/string enum | `true`, `false` | reason-required OFF에서 reason 관련 실패가 나오면 구현/상태 drift로 본다. |
| `entry_surface` | enum | `lock_screen`, `block_screen`, `unknown` | 진입 표면 bucket. |

기록 원칙:

- `missing_custom_reason`은 custom reason 원문을 절대 보내지 않는다.
- `missing_app_selection`은 선택 앱 개수나 목록을 보내지 않는다. 필요하면 후속에서 `selected_app_count_bucket` 같은 bucket만 별도 검토한다.
- validation 실패가 helper copy로 이미 설명되는지 QA에서 함께 확인한다.

### `emergency_unlock_cancelled`

사용자가 긴급해제 플로우를 닫거나 countdown에서 취소해 실제 임시 해제가 완료되지 않았을 때 기록한다.

| 파라미터 | 타입 | 허용값 | 설명 |
| --- | --- | --- | --- |
| `step_name` | enum | `reason`, `app_selection`, `duration`, `countdown`, `unknown` | 취소 시점의 현재 단계. |
| `cancel_source` | enum | `sheet_dismiss`, `back`, `cancel_button`, `outside_tap`, `system`, `unknown` | 가능한 경우 UI source를 bucket으로만 기록한다. |
| `reason_required_enabled` | boolean/string enum | `true`, `false` | reason 분모 confidence 분리. |
| `entry_surface` | enum | `lock_screen`, `block_screen`, `unknown` | 진입 표면 bucket. |

기록 원칙:

- countdown 취소는 실패가 아니라 사용자가 자기통제 유예에서 되돌아간 긍정/중립 신호일 수 있다.
- 취소 직전 입력한 custom reason, 선택 앱, duration raw value를 함께 보내지 않는다.

## 금지 payload / query 축

아래 값은 이벤트 payload, GA4 custom dimension, issue comment evidence, logcat evidence 모두에서 금지한다.

- custom reason 원문 또는 display label
- 앱 이름, 앱 package, 선택 앱 list, raw app count list
- raw timestamp, raw history/session snapshot
- raw duration option list 또는 설정 snapshot dump
- `manualResetAtMillis`, 긴급해제 상태 object dump
- 사용자를 낙인찍는 실패/중독/충동 원문

허용되는 값은 enum/bool/bucket뿐이다. PR #783 이후 `FirebaseKeepAnalyticsTest`와 bottom-sheet/ViewModel tests가 금지 key/value가 들어가지 않음을 고정한다. 새 step analytics PR은 이 payload boundary를 약화하면 안 된다.

## Reason-required ON/OFF 해석

- reason-required ON: `reason` step 노출, reason validation, custom reason validation을 모두 관측할 수 있다.
- reason-required OFF: 플로우는 app selection부터 시작한다. 이 경우 `emergency_unlock_step_viewed(step_name=reason)`이나 `validation_reason=missing_reason|missing_custom_reason`이 나오면 구현 drift로 본다.
- reason distribution은 reason-required ON 사용자에게만 대표성이 있다. 전체 긴급해제 flow friction은 `step_viewed`, `validation_blocked`, `cancelled`, `completed`를 함께 본다.

## 제품/지표 해석

권장 funnel:

1. `emergency_unlock_used`
2. `emergency_unlock_step_viewed(step_name=reason)` 또는 reason-required OFF면 `app_selection`
3. `emergency_unlock_step_viewed(step_name=app_selection)`
4. `emergency_unlock_step_viewed(step_name=duration)`
5. `emergency_unlock_step_viewed(step_name=countdown)`
6. `emergency_unlock_cancelled(step_name=countdown)` 또는 `emergency_unlock_completed`

우선 보는 진단:

- reason step validation이 높다 → reason copy, custom reason required/off policy, disabled helper copy를 점검한다.
- app selection validation이 높다 → blocked app list/selection affordance, “필요한 앱만” helper, zero-selection disabled copy를 점검한다.
- duration step validation이 높다 → duration option 설정/표시/CTA 활성화를 점검한다.
- countdown cancellation이 높다 → 사용자가 스스로 취소한 긍정 신호일 수 있으므로 완료율 저하로만 해석하지 않는다. 반복 차단/재방문/리뷰 guardrail과 같이 본다.

## GA4 Admin / readback 경계

Required custom dimensions:

- `customEvent:step_name`
- `customEvent:validation_reason`
- `customEvent:reason_required_enabled`
- `customEvent:entry_surface`
- `customEvent:cancel_source`

등록/측정 순서:

1. Android code-lane이 `emergency_unlock_step_viewed`, `emergency_unlock_validation_blocked`, `emergency_unlock_cancelled` API와 payload 테스트를 추가한다. (repo-internal 완료: PR #783 / `12c47108`)
2. GA4 Admin에서 위 custom dimensions를 등록한다.
3. metadata에서 `customEvent:*` 조회 가능성을 확인한다.
4. 해당 code 포함 release/tag/Play deploy 후 최소 14일 창에서 funnel/readback을 수행한다.
5. 30일 창에서 reason-required ON/OFF, app version, entry surface별 guardrail을 재확인한다.

GA4 Admin 등록 또는 release/tag/Play deploy 전의 0건은 adoption/UX 문제로 해석하지 않는다.

## Android implementation status

완료된 repo-internal wiring (PR #783 / merge commit `12c4710815746e79bde1a94fd5ad5f5d52fb81b7`):

- `KeepAnalytics` / `FirebaseKeepAnalytics`에 세 이벤트 API 추가.
- `AnalyticsEmergencyUnlockStepName`, `AnalyticsEmergencyUnlockValidationReason`, `AnalyticsEmergencyUnlockCancelSource`로 `step_name`, `validation_reason`, `cancel_source`, `entry_surface` 값을 제한.
- `EmergencyUnlockBottomSheetState`가 stable `analyticsStepName` / `validationReason`을 제공하고, `EmergencyUnlockBottomSheetContent`가 step view, validation-blocked state, countdown cancel을 ViewModel analytics callback으로 전달한다.
- `BlockScreen`은 `entry_surface=block_screen`, `LockScreen`은 `entry_surface=lock_screen`으로 기록한다.
- `FirebaseKeepAnalyticsTest`, `EmergencyUnlockBottomSheetStateTest`, `BlockViewModelTest`가 payload/enum/source contract를 고정한다.
- PR #783의 검증: `python3 -m unittest scripts.tests.test_emergency_unlock_step_analytics_contract -v`, focused `:app:testDevDebugUnitTest` for `FirebaseKeepAnalyticsTest` / `EmergencyUnlockBottomSheetStateTest` / Block source test, `git diff --check`, broader `:app:testDevDebugUnitTest :app:assembleProdDebug`, remote Branch Hygiene / Android CI / Ops CI green.

남은 경계:

- GA4 Admin custom dimension 등록과 metadata 재확인.
- SemVer release/tag/Play deploy 포함.
- 배포 후 D+14/D+30 readback.

## QA evidence template

```md
## Emergency unlock step analytics QA evidence
- Issue: #779
- Build / commit / variant:
- Device / Android version:
- Locale:
- Reason required: on / off
- Entry surface: lock_screen / block_screen
- Verification commands:
  - `python3 -m unittest scripts.tests.test_emergency_unlock_step_analytics_contract -v`
  - `./gradlew --console=plain :app:testDevDebugUnitTest --tests '*EmergencyUnlock*'`
  - `./gradlew --console=plain :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContentIntegrationTest`
- Step events:
  - reason step viewed only when reason required ON: pass / fail / n/a
  - app_selection step viewed: pass / fail
  - duration step viewed: pass / fail
  - countdown step viewed: pass / fail
- Validation events:
  - missing reason/custom reason uses enum only: pass / fail / n/a
  - missing app selection uses enum only and no app list/package: pass / fail
  - missing duration/options uses enum only: pass / fail / n/a
- Cancel events:
  - sheet dismiss/back/cancel button uses `cancel_source` enum: pass / fail
  - countdown cancel does not emit `emergency_unlock_completed`: pass / fail
- Privacy:
  - no custom reason raw text: pass / fail
  - no app name/package/list: pass / fail
  - no raw timestamp/history/duration list/settings snapshot: pass / fail
- GA4:
  - customEvent metadata registered: pass / fail / pending
  - 14-day readback scheduled after release: yes / no
```

## 완료 기준 매핑

Repo-internal 완료:

- [x] reason/app/duration/countdown 단계 노출·검증 실패·취소가 privacy-safe 이벤트로 기록된다. (`PR #783`)
- [x] custom reason 원문, app name/package/list, raw timestamp/history가 payload에 들어가지 않음을 테스트로 보장한다. (`PR #783`)
- [x] reason-required ON/OFF 양쪽 flow 테스트가 새 이벤트 계약을 검증한다. (`PR #783` + QA baseline)
- [x] GA4 등록 runbook, event dictionary, metrics/product docs, QA checklist, ops context pack에 신규 event/parameter readback 기준과 14일 확인 조건이 추가된다. (`PR #781`, `PR #798`)

외부/측정 경계:

- [ ] GA4 Admin에서 `customEvent:*` metadata를 실제 등록하고 metadata/readback을 확인한다.
- [ ] #779 포함 버전이 release/tag/Play deploy를 지난다.
- [ ] 배포 후 D+14/D+30 readback으로 단계별 이탈/검증 실패 분포를 해석한다.

PR #781은 계약과 운영 경계를 고정했고, PR #783은 Android 이벤트 구현을 `develop`에 반영했으며, PR #798은 post-wiring 문서/런북 동기화를 완료했다. 현재 남은 경계는 GA4 Admin 등록, release/tag/Play deploy, 14일/30일 readback이다.
