# 부모 모드 MVP 계약

Issue: #471

이 문서는 Stopit의 `부모 모드` / `아이에게 폰 주기` 기능을 같은 기기 안에서 먼저 검증하기 위한 제품·analytics·QA source of truth다. 원격 자녀 기기 관리는 VOC에 포함되어 있지만, 이번 MVP는 부모가 자신의 휴대폰을 아이에게 잠깐 넘기는 상황을 안전하게 다루는 **same-device MVP**로 제한한다.

## 문제와 제품 의도

부모가 자신의 휴대폰을 아이에게 잠깐 넘겨 영상이나 키즈 콘텐츠를 보여줄 때, 현재 Stopit은 “정해진 시간 동안만 허용 앱을 쓰고 시간이 끝나면 자연스럽게 끊기”를 직접 지원하지 않는다. 기존 수동 잠금/타이머/루틴은 자기통제 중심이라 부모가 일상적으로 쓰는 앱까지 함께 제약하거나, 아이가 시간 만료 후 계속 보려는 상황을 보호자 확인으로 정리하기 어렵다.

대표 사용 시나리오:

1. 부모가 홈 또는 메뉴에서 `아이에게 폰 주기`를 누른다.
2. 10분 / 20분 / 30분 / 직접 설정 중 시간을 고른다.
3. YouTube, Netflix, Kids 앱처럼 허용 앱을 선택한다.
4. 보호자 PIN을 확인하고 부모 모드를 시작한다.
5. 시간이 만료되면 허용 앱 사용이 종료되고, 보호자 PIN 없이는 연장/해제할 수 없다.

## MVP 범위

### 포함

- 진입점: Home secondary action 또는 Menu 항목 중 하나로 시작한다. 사용자-facing copy는 `아이에게 폰 주기`, 기능명은 `부모 모드`를 우선 후보로 둔다.
- 시간 선택: `10분`, `20분`, `30분`, `직접 설정`을 기본 프리셋으로 둔다.
- 허용 앱 선택: 부모 모드 중 사용할 수 있는 앱을 1개 이상 선택한다.
- 보호자 PIN: 시작 전 보호자 PIN 설정/확인, 종료/연장 전 PIN 확인을 요구한다.
- 시간 만료: 만료 시 허용 앱을 더 이상 계속 사용할 수 없고 Stopit의 차단 화면 또는 부모 모드 종료 화면으로 전환한다.
- 연장/종료: 부모 모드 중 시간 연장 또는 즉시 종료는 보호자 PIN 확인 후만 가능하다.
- 결과 요약: 종료 시 `시간 만료`, `PIN 해제`, `취소` 같은 비민감 상태만 보여준다.

### 제외 / 후속 후보

- 원격 자녀 기기 관리, 부모 폰에서 아이 폰/태블릿을 제어하는 다중 기기 연결은 MVP에서는 제외한다.
- 계정 기반 가족 그룹, 서버 동기화 정책 배포, FCM 기반 원격 시간 연장/해제는 MVP에서는 제외한다.
- 자녀별 프로필, 아이 이름 저장, 사용 앱 상세 리포트, 원격 스크린타임 리포트는 MVP에서는 제외한다.
- 결제/프리미엄, 광고 보상형 추가 시간, 광고 시청을 조건으로 한 해제/추가 시간 제공은 MVP에서는 제외한다.
- 기존 긴급해제와 분리한다. 부모 모드 PIN 해제는 보호자 확인 흐름이고, 차단 중 사용자 본인을 위한 긴급해제 quota/analytics와 섞지 않는다.

## 정책/상태 계약

부모 모드는 기존 수동 Keep, 타이머, 루틴, 목표 잠금과 별도의 session type으로 다룬다.

- `ParentModeSession` 후보 필드:
  - `startedAt`
  - `expiresAt`
  - `durationMinutes`
  - `allowedAppCount`
  - `allowedApps`는 내부 차단 판단용으로만 보관하고 analytics에는 원문을 보내지 않는다.
  - `pinConfigured` / `pinVerifiedAt`
  - `state`: `setup`, `active`, `expired`, `unlocked_by_pin`, `cancelled`
- PIN 저장은 원문 저장 금지다. 구현 시에는 해시/credential abstraction을 사용하고 로그·analytics·문서 evidence에 PIN 원문을 남기지 않는다.
- 허용 앱 목록은 접근성 차단 판단에는 필요하지만, analytics/query/share payload에는 앱 이름, package name, label, 전체 목록을 넣지 않는다.
- 부모 모드가 active일 때 기존 루틴/타이머와 충돌하면 더 보수적인 차단 정책을 우선한다. 단, 부모가 선택한 허용 앱은 부모 모드 session 안에서만 허용되며 전역 allowlist로 승격하지 않는다.
- 시스템 필수 기능, 긴급 전화, Android 안전/접근성 설정 화면은 앱이 안전하게 다룰 수 있는 한도 안에서 막지 않는다.

## UX 원칙

- 톤은 “아이를 통제/감시”가 아니라 “약속한 시간만 보기”다.
- 시작 전 부모가 무엇이 허용되고 무엇이 차단되는지 짧게 확인할 수 있어야 한다.
- 아이에게는 시간 만료 이유를 비난 없이 보여준다. 예: `약속한 시간이 끝났어요. 보호자에게 확인해 주세요.`
- 부모 PIN 없이는 시간 연장/종료가 불가능해야 한다. 다만 앱/기기 안전을 해치는 강한 anti-circumvention은 별도 후속으로 분리한다.
- 최근 앱, 설정, 알림, 접근성 우회처럼 same-device 우회 가능성이 높은 경로는 구현 PR의 runtime QA evidence에 포함한다.

## Privacy / Safety guardrail

- analytics payload 금지:
  - 아이 이름 원문
  - 앱 이름/package/raw session history
  - 허용 앱 원문 목록
  - PIN 원문, PIN 길이, PIN 실패 세부값
  - raw start/end timestamp
- 허용되는 analytics 형태:
  - duration bucket
  - allowed app count bucket
  - PIN 결과 enum
  - 종료 사유 enum
  - block context enum
- 로그 evidence에는 토큰, PIN, 앱 package, 아이 이름, 상세 시청/사용 이력을 남기지 않는다.
- 부모 모드와 기존 긴급해제를 혼동하지 않는다. 부모 모드 PIN 해제 성공을 `emergency_unlock_completed`로 기록하지 않는다.

## Analytics 계약

> 이 표는 PR #519의 policy/analytics foothold와 PR #584의 session/accessibility foothold 이후에도 유지되는 source of truth다. 코드에는 일부 `parent_mode_*` API와 `app_block_intercepted.block_source=parent_mode` 경계가 들어갔지만, setup/active UI, release/tag/Play deploy, GA4 Admin 등록/metadata 확인 전에는 세부 breakdown 결론을 낮은 confidence로 둔다.

| 이벤트명 | 주요 파라미터 | 의미 |
| --- | --- | --- |
| `parent_mode_duration_selected` | `duration_minutes_bucket` | 부모 모드 setup에서 사용 시간이 선택됨 |
| `parent_mode_allowed_apps_selected` | `allowed_app_count_bucket` | 허용 앱 1개 이상 선택 완료 |
| `parent_mode_started` | `duration_minutes_bucket`, `allowed_app_count_bucket` | 보호자 PIN 확인 후 부모 모드 시작 |
| `parent_mode_completed` | `duration_minutes_bucket`, `end_reason` | 시간 만료 또는 정상 종료로 session 완료 |
| `parent_mode_unlocked_by_pin` | `pin_result`, `end_reason` | 보호자 PIN으로 해제/연장 흐름 통과 |
| `parent_mode_extended` | `extension_minutes_bucket` | 보호자 PIN 확인 후 시간 연장 |
| `parent_mode_block_intercepted` | `block_context` | 부모 모드 중 허용되지 않은 앱/우회 surface 차단 |
| `parent_mode_cancelled` | `end_reason` | 시작 전 또는 active 중 취소 |

Parameter enum/bucket 후보:

- `duration_minutes_bucket`: `1_9`, `10`, `11_20`, `21_30`, `31_60`, `61_plus`
- `extension_minutes_bucket`: `1_9`, `10`, `11_20`, `21_30`, `31_plus`
- `allowed_app_count_bucket`: `1`, `2_3`, `4_6`, `7_plus`
- `pin_result`: `success`, `failure`, `not_configured`
- `end_reason`: `time_expired`, `pin_unlocked`, `cancelled_before_start`, `cancelled_by_parent`, `system_interrupted`, `unknown`
- `block_context`: `disallowed_app`, `settings_surface`, `recent_apps`, `notification_surface`, `unknown`

## 측정 지표

### Success metrics

- 시작 전환: `parent_mode_started` users / `parent_mode_duration_selected` users
- setup 완주: `parent_mode_started` users / `parent_mode_allowed_apps_selected` users
- 시간 만료 완료: `parent_mode_completed(end_reason=time_expired)` users / `parent_mode_started` users
- 보호자 개입률: `parent_mode_unlocked_by_pin` users / `parent_mode_started` users

### Guardrails

- `parent_mode_block_intercepted(block_context=settings_surface|recent_apps|notification_surface)`가 급증하면 우회/UX 불안을 먼저 본다.
- `pin_result=failure` 비율이 높으면 부모가 PIN을 잊었거나 아이가 반복 시도하는 UX 리스크로 본다. 실패 횟수 원문이나 PIN 관련 세부값은 기록하지 않는다.
- 기존 `first_core_action_completed`, `app_block_intercepted`, 루틴/타이머 사용률이 악화되면 부모 모드 진입점이 핵심 자기통제 흐름을 방해하는지 확인한다.
- review/rating 악화 또는 VOC에서 “아이를 가둔다/불편하다” 신호가 나오면 copy와 escape path를 재검토한다.

## QA baseline

구현 PR은 최소 아래 contract를 남긴다.

### JVM/policy baseline 후보

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.feature.parentmode.ParentModePolicyTest" \
  --tests "com.uiery.keep.feature.parentmode.ParentModePinPolicyTest" \
  --tests "com.uiery.keep.feature.parentmode.ParentModeSetupViewModelTest" \
  --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly"
```

검증 범위:

- duration preset/custom validation
- allowed app count 1개 이상 validation
- PIN 미설정/성공/실패 policy
- 만료/연장/종료 state transition
- analytics payload가 bucket/enum만 사용하고 앱 이름/package/PIN 원문을 보내지 않는지

### Runtime/manual baseline 후보

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest#activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution
```

수동 QA evidence는 `docs/QA_RUNTIME_CHECKLIST.md`의 `Parent mode QA evidence` 템플릿을 사용한다.

확인 포인트:

- same-device / PIN / bypass 경계:
  - [ ] 부모 PIN 확인 후에만 부모 모드가 시작된다.
  - [ ] 선택한 허용 앱은 시간 안에서 열 수 있다.
  - [ ] 허용되지 않은 앱은 차단된다.
  - [ ] 시간이 끝나면 허용 앱도 계속 사용할 수 없다.
  - [ ] PIN 없이 시간 연장/종료가 되지 않는다.
  - [ ] PIN 성공 시 양수 extension만 즉시 연장되고, 0분/음수 extension은 거부된다.
  - [ ] PIN 성공 시 즉시 종료가 된다.
  - [ ] 최근 앱, 설정, 알림 surface로 쉽게 우회되지 않는다.
  - [ ] 긴급 전화/필수 시스템 safety path를 부적절하게 막지 않는다.

## 구현 handoff

### 1차 code-lane foothold

2026-06-06 code-lane PR #519에서 첫 repo-internal foothold를 추가했다. 이 foothold는 아직 entrypoint/setup UI/Accessibility runtime 연결은 아니지만, 후속 구현이 공유해야 할 순수 정책과 privacy-safe analytics schema를 코드 계약으로 고정한다.

- `feature/parentmode/ParentModePolicy.kt`: duration/app-count/state transition/pin-result pure policy, active session expiry, allowlist 기반 block decision
- `ParentModePolicyTest`, `ParentModePinPolicyTest`: RED/GREEN 첫 계약
- `KeepAnalytics` / `FirebaseKeepAnalytics`: `parent_mode_*` event API 추가
- `FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly`, `parentModeCompletedDoesNotSendRawTimestampsOrPackages`: privacy-safe parameter 회귀

### 2차 code-lane foothold

2026-06-07 code-lane PR #584에서 부모 모드 session persistence와 AccessibilityService 차단 판단 연결을 repo-internal runtime foothold로 추가했고, merge commit `b58c6a8dbf2ba4541a748da4d0b948ee8c6a692a`로 `develop`에 반영됐다. 아직 setup/active UI와 device/emulator bind evidence는 남아 있지만, 저장된 부모 모드 session이 실제 foreground block decision에 들어가는 경계는 코드와 테스트로 고정했다.

- `ParentModeSessionStore`: `PreferencesKey.PARENT_MODE_STARTED_AT`, `PARENT_MODE_EXPIRES_AT`, `PARENT_MODE_DURATION_MINUTES`, `PARENT_MODE_ALLOWED_APPS`, `PARENT_MODE_STATE`를 DataStore에 저장/관찰한다.
- `BackupRestoreDataStoreKeyPolicy`: 부모 모드 session key를 restore-reset-only로 유지해 기기 복원 후 아이에게 폰 주기 session이 되살아나지 않게 한다.
- `KeepAccessibilityServiceBlockDecisionTest`: active 부모 모드가 허용되지 않은 앱을 `block_source=parent_mode`로 차단하고, 시간 만료 후 허용 앱도 차단하는 순수 decision 경계를 검증한다. 단, Stopit 앱처럼 보호자 PIN/종료/연장 진입에 필요한 부모 제어 surface는 차단하지 않는다.
- `KeepAccessibilityService`: `ParentModeSessionStore.observe()`를 구독하고 foreground 재평가에 부모 모드 session을 전달한다.
- `AnalyticsBlockSource.PARENT_MODE`: `app_block_intercepted.block_source`에 `parent_mode` 값을 추가했다.

### 3차 code-lane foothold

2026-06-09 code-lane PR에서 `ParentModeSessionController`를 추가해 setup validation → session 저장 → privacy-safe analytics commit, PIN 검증 후 연장/즉시 종료 commit 경계를 한 곳으로 묶었다. 이 foothold는 아직 화면 진입점이나 실제 PIN 입력 UI가 아니지만, 후속 Home/Menu/setup/active 화면은 이 controller를 통해서만 부모 모드 session을 시작·연장·종료해야 한다.

- `ParentModeSessionController`: duration/허용 앱/PIN validation 실패 시 저장·analytics를 하지 않고 `SetupBlocked`를 반환한다.
- `ParentModeSessionControllerTest`: 시작, invalid setup, PIN 없는 연장 거부, PIN 성공 연장, PIN 성공 즉시 종료, 시간 만료 1회 commit을 DataStore 저장값과 analytics call 순서까지 검증한다.
- `parent_mode_started`, `parent_mode_completed`, `parent_mode_unlocked_by_pin`, `parent_mode_extended`는 raw 앱 package/PIN/session history 없이 bucket/enum만 보낸다.

### 4차 code-lane foothold

2026-06-09 code-lane PR에서 Menu의 `아이에게 폰 주기` entrypoint와 `ParentModeSetupRoute`/setup 화면 foothold를 추가했다. 이 foothold는 사용자가 앱 안에서 부모 모드 준비 화면까지 도달하고 현재 선택 앱을 setup allowed-app seed로 읽어오는 경계를 코드와 JVM 테스트로 고정한다.

- `MenuScreen` / `MenuNavigation` / `KeepApp`: Menu에서 부모 모드 setup route로 이동하는 entrypoint를 연결한다.
- `ParentModeSetupScreen`: 현재 선택 앱 수와 보호자 PIN 입력 필드를 보여주고, verified PIN일 때만 setup CTA를 활성화한다.
- `ParentModeSetupViewModelTest`: 현재 차단 선택 앱을 부모 모드 허용 앱으로 seed하고, PIN 불일치/미충족 상태에서는 session 저장을 막는 경계를 검증한다.

### 5차 code-lane foothold

2026-06-09 code-lane PR에서 실제 PIN 입력 UI와 setup CTA enablement를 setup 화면에 연결했다. 이 foothold는 active/expired 화면을 완성하지는 않지만, 사용자가 보호자 PIN을 입력·확인한 뒤에만 `ParentModeSessionController`를 통해 session 저장과 `Started` side effect를 발생시키는 runtime setup 경계를 고정한다.

- `ParentModeSetupScreen`: 보호자 PIN / 확인 입력 필드, mismatch helper, numeric password keyboard, verified PIN 기반 시작 CTA를 제공한다.
- `ParentModeSetupViewModel`: digit-only 4~6자리 PIN 입력을 state에 반영하고, `ParentModePinState.Verified`일 때만 `canAttemptStart`를 true로 만든다.
- `ParentModeSetupViewModelTest`: PIN mismatch, 짧은 PIN, non-digit filtering, verified PIN start path를 `ParentModeSessionController` 저장/analytics call 경계와 함께 검증한다.

### 6차 code-lane foothold

2026-06-09 code-lane PR에서 `ParentModeSessionController.markExpiredIfNeeded(...)`를 추가해 active session이 만료 시각을 지난 뒤 `expired` 상태와 `parent_mode_completed(end_reason=time_expired)` analytics를 한 번만 commit하도록 고정했다. Accessibility 차단 decision은 이미 만료 세션에서 허용 앱도 차단하지만, 이번 foothold는 persisted session state와 completion analytics가 중복 없이 따라오도록 보강한다.

- `ParentModeSessionController`: active session만 만료 처리하고, 이미 `expired`/`unlocked_by_pin`/`cancelled` 상태인 session은 `NoStateChange`로 둔다.
- `ParentModeSessionControllerTest.markExpiredIfNeededPersistsExpiredSessionAndTracksCompletionOnce`: 만료 1회 저장, 두 번째 호출 no-op, `time_expired` completion event 1회만 기록되는 경계를 검증한다.

### 7차 QA-lane runtime foothold

2026-06-09 QA-lane PR에서 active Parent Mode session을 실제 AccessibilityService runtime baseline에 연결했다. 이 foothold는 full active/expired UX 화면을 완성하지는 않지만, device/emulator에서 저장된 Parent Mode session을 서비스가 관찰하고 허용되지 않은 foreground 앱에 대해 `block_source=parent_mode` BlockActivity 요청을 남기는 evidence를 고정한다.

- `KeepAccessibilityServiceDebugState`: 서비스가 관찰한 Parent Mode state와 allowed-app count를 instrumentation evidence로 보존한다.
- `KeepAccessibilityServiceIntegrationTest.activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution`: manual Keep 없이 active Parent Mode DataStore session만으로 비허용 앱 차단 요청이 발생하고, `observedParentModeState=active`, `observedParentModeAllowedAppCount=1`, `lastLaunchedBlockSource=parent_mode`가 기록되는지 검증한다.
- `docs/QA_RUNTIME_CHECKLIST.md`: Parent Mode runtime baseline command와 evidence 템플릿을 실제 service integration test로 동기화한다.

### 다음 code-lane 후보

- Parent mode active/expired screen
- Runtime instrumentation: `ParentModeAccessibilityIntegrationTest`

남은 범위는 MVP 전체 UX/릴리스/실측 검증이다. 이미 반영된 repo-internal foothold를 “구현 전” 상태로 되돌리지 말고, 다음 실행 lane은 active/expired 화면, 실제 device/emulator evidence를 이어 붙이는 방향으로 잡는다.

### 후속 별도 이슈 후보

- 원격 부모폰에서 아이 폰/태블릿 관리
- 가족 계정/기기 연결
- 원격 연장/해제 승인
- 자녀별 프로필/정책 템플릿
- 강한 anti-circumvention mode

## Closing discipline

- 이 문서는 PR #519와 PR #584 이후의 repo-internal foothold 상태를 반영한 source of truth다. 후속 docs sync나 code-lane PR은 acceptance 전체를 만족하지 못하면 계속 `Refs #471`를 사용한다.
- `Closes #471`는 부모 모드 entrypoint, setup/active/expired UI, PIN 확인 runtime flow, time expiry, Accessibility runtime 차단, privacy-safe analytics, QA evidence가 모두 구현·검증된 PR에서만 사용한다.
- GA4 Admin 등록, release/tag/Play deploy, 14일/30일 readback은 구현 완료 뒤의 외부/manual boundary로 별도 기록한다.

## Contract regression

문서 계약이 drift되지 않도록 아래 테스트를 사용한다.

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_parent_mode_contract -v
```

이 `parent-mode contract regression`은 `docs/PARENT_MODE_MVP.md`, analytics dictionary, GA4 runbook, product/metrics context, runtime QA checklist, docs AGENTS 링크가 함께 유지되는지 확인한다.
