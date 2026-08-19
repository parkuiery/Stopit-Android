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

> 이 표는 PR #519의 policy/analytics foothold와 PR #584의 session/accessibility foothold 이후에도 유지되는 source of truth다. 코드에는 `parent_mode_*` API, `app_block_intercepted.block_source=parent_mode`, 그리고 Parent Mode 차단 화면 진입 시 `parent_mode_block_intercepted(block_context=disallowed_app)`를 함께 남기는 경계가 들어갔다. 다만 release/tag/Play deploy, GA4 Admin 등록/metadata 확인 전에는 live 세부 breakdown 결론을 낮은 confidence로 둔다.

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
  --tests "com.uiery.keep.BlockViewModelTest.parentModeBlockTracksDedicatedPrivacySafeInterceptEvent" \
  --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeStartedUsesSafeBucketedParamsOnly" \
  --tests "com.uiery.keep.analytics.FirebaseKeepAnalyticsTest.parentModeBlockInterceptedUsesSafeBlockContextOnly"
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

2026-06-07 code-lane PR #584에서 부모 모드 session persistence와 AccessibilityService 차단 판단 연결을 repo-internal runtime foothold로 추가했고, merge commit `b58c6a8dbf2ba4541a748da4d0b948ee8c6a692a`로 `develop`에 반영됐다. 이 시점에는 setup/active UI와 device/emulator bind evidence가 후속 경계였지만, 이후 PR #748/#870/#873/#897에서 active controls·직접 분 입력·접근성 요약·dedicated Parent Mode block analytics까지 이어졌다. 따라서 이 2차 foothold의 현재 의미는 저장된 부모 모드 session이 실제 foreground block decision에 들어가는 하위 runtime 경계를 코드와 테스트로 고정했다는 점이다.

- `ParentModeSessionStore`: `PreferencesKey.PARENT_MODE_STARTED_AT`, `PARENT_MODE_EXPIRES_AT`, `PARENT_MODE_DURATION_MINUTES`, `PARENT_MODE_ALLOWED_APPS`, `PARENT_MODE_STATE`를 DataStore에 저장/관찰한다.
- `BackupRestoreDataStoreKeyPolicy`: 부모 모드 session key를 restore-reset-only로 유지해 기기 복원 후 아이에게 폰 주기 session이 되살아나지 않게 한다.
- `KeepAccessibilityServiceBlockDecisionTest`: active 부모 모드가 허용되지 않은 앱을 `block_source=parent_mode`로 차단하고, 시간 만료 후 허용 앱도 차단하는 순수 decision 경계를 검증한다. 단, Stopit 앱처럼 보호자 PIN/종료/연장 진입에 필요한 부모 제어 surface는 차단하지 않는다.
- `KeepAccessibilityService`: `ParentModeSessionStore.observe()`를 구독하고 foreground 재평가에 부모 모드 session을 전달한다.
- `AnalyticsBlockSource.PARENT_MODE`: `app_block_intercepted.block_source`에 `parent_mode` 값을 추가했다.
- `BlockViewModelTest.parentModeBlockTracksDedicatedPrivacySafeInterceptEvent`: Parent Mode로 열린 차단 화면이 기존 `app_block_intercepted(block_source=parent_mode)`와 별도로 `parent_mode_block_intercepted(block_context=disallowed_app)`를 남기고, dedicated Parent Mode 이벤트에는 앱 package/PIN/session 원문을 넣지 않는 경계를 검증한다.

### 3차 code-lane foothold

이 code-lane foothold에서 `ParentModeSessionController`를 추가해 setup validation → session 저장 → privacy-safe analytics commit, PIN 검증 후 연장/즉시 종료 commit 경계를 한 곳으로 묶었다. 이 foothold는 아직 화면 진입점이나 실제 PIN 입력 UI가 아니지만, 후속 Home/Menu/setup/active 화면은 이 controller를 통해서만 부모 모드 session을 시작·연장·종료해야 한다.

- `ParentModeSessionController`: duration/허용 앱/PIN validation 실패 시 저장·analytics를 하지 않고 `SetupBlocked`를 반환한다.
- `ParentModeSessionControllerTest`: 시작, invalid setup, PIN 없는 연장 거부, PIN 성공 연장, PIN 성공 즉시 종료, 시간 만료 1회 commit을 DataStore 저장값과 analytics call 순서까지 검증한다.
- `parent_mode_started`, `parent_mode_completed`, `parent_mode_unlocked_by_pin`, `parent_mode_extended`는 raw 앱 package/PIN/session history 없이 bucket/enum만 보낸다.

### 4차 code-lane foothold

이 code-lane foothold에서 Menu의 `아이에게 폰 주기` entrypoint와 `ParentModeSetupRoute`/setup 화면 foothold를 추가했다. 이 foothold는 사용자가 앱 안에서 부모 모드 준비 화면까지 도달하는 경계를 코드와 JVM 테스트로 고정한다. 당시에는 현재 차단 선택 앱을 setup allowed-app seed로 읽어왔지만, 아래 `10차 code-lane allowlist 경계`에서 그 seed를 걷어냈다.

- `MenuScreen` / `MenuNavigation` / `KeepApp`: Menu에서 부모 모드 setup route로 이동하는 entrypoint를 연결한다.
- `ParentModeSetupScreen`: 현재 선택 앱 수와 보호자 PIN 입력 필드를 보여주고, verified PIN일 때만 setup CTA를 활성화한다.
- `ParentModeSetupViewModelTest`: PIN 불일치/미충족 상태에서는 session 저장을 막는 경계를 검증한다.

### 5차 code-lane foothold

이 code-lane foothold에서 실제 PIN 입력 UI와 setup CTA enablement를 setup 화면에 연결했다. 이 foothold는 active/expired 화면을 완성하지는 않지만, 사용자가 보호자 PIN을 입력·확인한 뒤에만 `ParentModeSessionController`를 통해 session 저장과 `Started` side effect를 발생시키는 runtime setup 경계를 고정한다.

- `ParentModeSetupScreen`: 보호자 PIN / 확인 입력 필드, mismatch helper, numeric password keyboard, verified PIN 기반 시작 CTA를 제공한다.
- `ParentModeSetupViewModel`: digit-only 4~6자리 PIN 입력을 state에 반영하고, `ParentModePinState.Verified`일 때만 `canAttemptStart`를 true로 만든다.
- `ParentModeSetupViewModelTest`: PIN mismatch, 짧은 PIN, non-digit filtering, verified PIN start path를 `ParentModeSessionController` 저장/analytics call 경계와 함께 검증한다.

### 6차 code-lane foothold

이 code-lane foothold에서 `ParentModeSessionController.markExpiredIfNeeded(...)`를 추가해 active session이 만료 시각을 지난 뒤 `expired` 상태와 `parent_mode_completed(end_reason=time_expired)` analytics를 한 번만 commit하도록 고정했다. Accessibility 차단 decision은 이미 만료 세션에서 허용 앱도 차단하지만, 이번 foothold는 persisted session state와 completion analytics가 중복 없이 따라오도록 보강한다.

- `ParentModeSessionController`: active session만 만료 처리하고, 이미 `expired`/`unlocked_by_pin`/`cancelled` 상태인 session은 `NoStateChange`로 둔다.
- `ParentModeSessionControllerTest.markExpiredIfNeededPersistsExpiredSessionAndTracksCompletionOnce`: 만료 1회 저장, 두 번째 호출 no-op, `time_expired` completion event 1회만 기록되는 경계를 검증한다.

### 7차 QA-lane runtime foothold

PR #714 merge commit `1a55a4a0a5969cca3a69f158721224e27f37002d`에서 active Parent Mode session을 실제 AccessibilityService runtime baseline에 연결했다. 이 시점에는 full active/expired UX 화면이 별도 후속 경계였지만, 이후 PR #748/#870/#873에서 setup/active/expired controls와 접근성 요약 baseline이 이어졌고, 이 7차 foothold는 device/emulator에서 저장된 Parent Mode session을 서비스가 관찰하고 허용되지 않은 foreground 앱에 대해 `block_source=parent_mode` BlockActivity 요청을 남기는 evidence로 남는다.

- `KeepAccessibilityServiceDebugState`: 서비스가 관찰한 Parent Mode state와 allowed-app count를 instrumentation evidence로 보존한다.
- `KeepAccessibilityServiceIntegrationTest.activeParentModeWithoutManualKeep_launchesBlockActivityWithParentModeAttribution`: manual Keep 없이 active Parent Mode DataStore session만으로 비허용 앱 차단 요청이 발생하고, `observedParentModeState=active`, `observedParentModeAllowedAppCount=1`, `lastLaunchedBlockSource=parent_mode`가 기록되는지 검증한다.
- `docs/QA_RUNTIME_CHECKLIST.md`: Parent Mode runtime baseline command와 evidence 템플릿을 실제 service integration test로 동기화한다.

### 8차 QA-lane expiry runtime foothold

PR #716 merge commit `04c8d075bf84081c78ce17748f368c9965acbbb2`에서 Parent Mode active session이 foreground 앱을 허용한 채 만료되는 순간에도 AccessibilityService가 time-based 재평가를 예약하도록 보강했다. 이전 foothold는 새 window event가 들어오면 expired policy로 차단할 수 있었지만, 같은 앱이 계속 foreground에 머무르는 동안 만료 시각을 지나는 케이스는 서비스가 다시 판단해야 하는 runtime 경계가 남아 있었다.

- `nextParentModeExpirationReevaluationDelayMillis(...)`: active Parent Mode session의 `expiresAtMillis`까지 남은 시간을 계산하고, 이미 만료됐거나 active가 아닌 session은 timer를 만들지 않는다.
- `nextTimeBasedBlockingStartReevaluationDelayMillis(...)`: Routine/Goal Lock 시작 시각뿐 아니라 Parent Mode 만료 시각도 다음 foreground 재평가 후보에 포함한다.
- `KeepAccessibilityService`: Parent Mode session 관찰 후 time-based 재평가 timer를 다시 예약하고, debug state는 persisted `active` 값만 쓰지 않고 현재 시각 기준 resolved `expired` state를 evidence로 남긴다.
- `KeepAccessibilityServiceIntegrationTest.expiredActiveParentModeWithoutManualKeep_blocksPreviouslyAllowedAppWithExpiredEvidence`: 저장된 active session의 만료 시각이 지난 경우, 원래 허용 앱도 `block_source=parent_mode`로 차단되고 `observedParentModeState=expired`가 기록되는 device/emulator baseline을 추가한다.

### 9차 code-lane active controls foothold

PR #748 merge commit `d73dac88c2bab17b446f4a1b9cd3a9b26ad1134d`로 Parent Mode setup 화면이 active/expired 상태까지 이어지는 제어 화면으로 `develop`에 반영됐다. 사용자는 setup에서 10/20/30분 preset을 선택하고, session 시작 후 같은 화면에서 active 상태를 확인하며, verified guardian PIN 상태로 10분 연장 또는 즉시 종료를 요청할 수 있다. 또한 화면 진입/상태 렌더링 시 `markExpiredIfNeeded(...)`를 호출해 만료된 session을 `expired` state와 `parent_mode_completed(end_reason=time_expired)` 1회 commit으로 동기화한다.

PR #870 merge commit `53e3d25c591c8fa8e2e444bff6636b046b2bd4eb`로 같은 setup 화면의 `직접 설정` 시간 선택 runway가 `develop`에 반영됐다. `ParentModeSetupViewModel.updateCustomDurationInput(...)`은 숫자만 받아 custom minute 값을 `durationMinutes` source of truth로 동기화하고, setup UI는 preset chip 옆에 직접 입력 필드를 제공한다. 따라서 Parent Mode MVP의 시간 선택은 이제 10/20/30분 preset뿐 아니라 직접 분 단위 입력까지 repo-internal baseline에 포함된다.

PR #873 merge commit `d1be39ae764b53386baeba8bfc1fa3c400ff941e` 이후 setup/active/expired 화면의 접근성 요약도 repo-internal baseline에 포함된다. `ParentModeSetupScreenAccessibilityTest`는 setup summary, active/expired TalkBack summary, 직접 입력 필드, 연장/종료 CTA enabled/disabled 상태를 Compose instrumentation으로 반복 검증한다. PR #946 merge commit `b3a6c7a121e88c56353372cbb97366b2a04c0bce` 이후 active controls는 fresh guardian PIN 입력/확인 필드를 노출하고, verified PIN이 다시 입력되기 전에는 연장/즉시 종료 CTA가 disabled 상태로 남는 baseline을 추가했다. 이 baseline은 실제 release-candidate 기기의 스크린샷/TalkBack spot-check를 대체하지는 않지만, docs/QA lane이 더 이상 "Parent Mode active/expired TalkBack baseline 미정의" 또는 "PIN 없는 active 연장/종료 허용" 상태로 되돌리지 않도록 한다.

PR #883 merge commit `2ea625f3bdb082966332ac8d5e28ae870ad3838a`에서 issue #874의 stale Active 액션 경계를 닫았다. active controls가 열린 채 `expiresAtMillis`를 지나면 화면은 만료 시각까지 delay 후 재조회하고, 연장/즉시 종료 액션도 먼저 만료를 확정한다. 따라서 만료된 session은 verified PIN이 있어도 stale expiry 기준으로 10분 연장되지 않고, `unlocked_by_pin`으로 오계측되지 않으며, `expired` state + `parent_mode_completed(end_reason=time_expired)`로 1회 commit된다. PR #1078 merge commit `1099043541598bbf0d82ce8fd1624c36c3eff8b9` 이후에는 이미 종료된 `expired` / `unlocked_by_pin` / `cancelled` session에 `extend(...)` 또는 `endNow(...)`가 다시 호출되어도 session을 재활성화하지 않고 추가 `parent_mode_completed` analytics를 남기지 않는 controller guard까지 repo-internal baseline에 포함된다.

- `ParentModePolicy`: parent action 요청 시 현재 시각 기준 `Expired`를 PIN 성공/실패보다 먼저 판정한다.
- `ParentModeSessionController`: `extend(...)` / `endNow(...)` 모두 expired active session을 `TIME_EXPIRED` completion으로 저장하고 연장/핀 종료 analytics를 보내지 않는다. 이미 `expired`/`unlocked_by_pin`/`cancelled`로 저장된 finished session에는 PR #1078 이후 연장/즉시 종료를 다시 적용하지 않고 `NoStateChange`로 둬서 재활성화와 completion analytics 중복 전송을 막는다.
- `ParentModeSetupScreen`: active session의 `expiresAtMillis`까지 남은 시간을 계산해 자동 refresh를 예약한다.
- `ParentModePolicyTest`, `ParentModeSessionControllerTest`, `ParentModeSetupViewModelTest`: stale Active 연장/종료 차단, finished session 연장/종료 no-op, 자동 refresh delay 계산, `TIME_EXPIRED` analytics 경계를 검증한다.

- `ParentModeSetupScreen`: duration preset 선택 UI와 직접 분 입력 필드, active/expired/ended status copy, active controls fresh guardian PIN 입력/확인 필드, 10분 연장 CTA, 보호자 PIN 종료 CTA, finished session 이후 `부모 모드 다시 시작` CTA를 제공한다.
- `ParentModeSetupViewModel`: setup 화면에서 `ParentModeSessionController.extend(...)`, `endNow(...)`, `markExpiredIfNeeded(...)`, `clearFinishedSession(...)`를 호출해 session 저장소와 화면 state를 함께 갱신하고, active controls가 fresh PIN 재입력 전에는 연장/종료를 시도하지 못하도록 guardian PIN input을 검증한다. Active session은 새 setup으로 지우지 않고, expired/unlocked_by_pin 같은 finished session만 clear 후 다른 부모 모드 setup으로 돌아간다.
- `ParentModeSetupViewModelTest`: 직접 입력한 custom duration으로 session이 시작되는지, active controls fresh guardian PIN 재입력 전 연장/종료가 차단되는지, verified PIN 기반 10분 연장, 즉시 종료, 만료 상태 동기화, finished session clear 후 다른 setup 재진입이 DataStore session과 화면 `activeSession`에 반영되는지 검증한다.

### 10차 code-lane allowlist 경계

VOC "부모모드를 사용하면 모든 앱이 잠긴다"를 확인한 결과, 재현되는 동작은 사양 그대로였다. 부모 모드는 허용목록이라
허용 앱으로 고르지 않은 앱은 전부 잠긴다. 문제는 그 사실을 사용자가 알 수 있는 지점이 하나도 없었다는 것이다.

- 씨딩 제거: `ParentModeSetupViewModel`이 더 이상 `BlockingStateStore`를 읽지 않는다. 차단 선택 앱을 허용 앱으로
  seed하면 부모가 평소 막아 두던 앱만 열리고 기기 전체가 잠긴다. 화면 진입은 `refreshActiveSessionStatus()`만 호출하고,
  허용 앱은 부모가 직접 고른다. `홈 선택 다시 불러오기` CTA와 `parent_mode_setup_reload_current_selection`도 함께 걷어냈다.
- 차단 범위 고지: setup 화면이 시작 CTA 위에서 `parent_mode_setup_allowed_apps_scope_notice`로 "고른 N개 외 모든 앱이
  잠긴다"를 명시한다. UX 원칙의 "시작 전 부모가 무엇이 허용되고 무엇이 차단되는지 짧게 확인할 수 있어야 한다"를 코드로 고정한 것이다.
- 허용 모드 시트 copy: `AppSelectionPurpose`(`Block`/`Allow`)를 공용 앱 선택 시트에 추가했다. `Allow`에서는 제목이
  `app_selection_allowed_apps_title`이 되고 `app_selection_allowed_apps_notice`가 함께 붙으며, 민감 앱 차단 확인
  다이얼로그는 뜨지 않는다. 그 다이얼로그는 "차단하면 인증번호를 못 받는다"고 경고하고 제외 버튼이 선택에서 앱을 빼는데,
  허용목록에서는 그 제외가 곧 차단이라 의미가 정반대다.
- 회귀: `ParentModeSetupViewModelTest.openingParentModeSetupLeavesAllowedAppsEmptyInsteadOfSeedingTheBlockingSelection`,
  `AppSelectionPurposeCopyPolicyTest`, `CategoryBottomSheetContentIntegrationTest`의 allow-purpose 2건,
  `scripts.tests.test_parent_mode_contract`의 setup/시트 copy 계약.

남은 VOC 후속 후보는 이 경계에 포함하지 않았다: 차단 화면의 부모 모드 전용 copy, 만료 상태 해제 CTA 문구,
`시스템 필수 기능/설정 화면은 막지 않는다`는 문서 계약과 현재 구현(허용하지 않은 설정 앱 차단)의 불일치.

### 11차 code-lane 차단 이유 · 탈출 경로 · 긴급 전화 경계

10차에서 부모가 시작 전에 무엇이 잠기는지 알게 됐다면, 이번 경계는 잠긴 뒤를 다룬다. 폰을 들고 있는 쪽은
세션을 만든 사람이 아니라서, 왜 멈췄는지·어떻게 푸는지·긴급 상황에 무엇을 할 수 있는지가 모두 화면에 없었다.

- 차단 이유 노출: `ParentModeRuntimePolicy.blockReason(...)`이 `AllowedAppsOnly` / `TimeExpired`를 나누고,
  `ParentModeBlockReasonSource`(public seam, 세션 원문은 넘기지 않는다)를 통해 `BlockUiState.parentModeBlockReason`으로
  들어간다. 차단 화면은 루틴 사유 칩과 같은 자리에 `block_screen_parent_mode_allowed_apps_reason` /
  `block_screen_parent_mode_expired_reason`을 띄운다. UX 원칙의 "아이에게는 시간 만료 이유를 비난 없이 보여준다"를
  코드로 고정한 것이다.
- 만료 탈출 경로 문구: `ParentModePolicy.finishedSessionAction(...)`이 `Expired`에서만 `EndAndUnlock`을 돌려주고,
  setup 화면은 그때 `parent_mode_expired_end_and_unlock`을 쓴다. 만료 상태는 모든 앱이 잠긴 상태이고 이 버튼이
  유일한 해제인데, 이전에는 `부모 모드 다시 시작`으로 적혀 있어 해제 경로가 새 잠금을 거는 이름 뒤에 숨어 있었다.
- 긴급 전화 발신: 부모 모드 세션이 유효한 동안(`Active`/`Expired`) 다이얼러는 허용 앱 여부와 무관하게 차단하지 않는다.
  기존 예외는 통화가 이미 걸려온 동안뿐이라 발신이 막혔고, 허용 앱에 다이얼러를 넣을지는 부모가 결정하는 값이라
  아이가 전화를 걸 수 있는지가 부모의 설정 실수에 달려 있었다. 자기통제 잠금(수동/타이머/루틴/목표)은 그대로
  발신을 막는다 — 거기서는 막은 사람과 거는 사람이 같다.
- 회귀: `ParentModeRuntimePolicyTest`의 blockReason 2건, `ParentModePolicyTest.finishedSessionActionSaysUnlockOnlyWhileTheDeviceIsStillLocked`,
  `KeepAccessibilityServiceBlockDecisionTest`의 다이얼러 3건(부모 모드 활성/만료 예외 + 자기통제 잠금 비예외),
  `BlockViewModelTest`의 reason 전달 2건, `BlockScreenContentIntegrationTest`의 부모 모드 사유 2건.

여전히 남은 경계: 보호자 PIN은 저장되지 않아 지금은 입력 확인란이다(`pinState`는 두 입력이 일치하는지만 본다).
따라서 만료 해제 CTA에 PIN 게이트를 다는 것은 실효가 없고, 선행 작업은 PIN 해시 영속화다. 허용하지 않은 설정 앱을
차단하는 현재 구현과 `시스템 필수 기능/설정 화면은 막지 않는다`는 위 계약의 불일치도 미해결로 남는다.

### 다음 경계

- repo-internal baseline: PR #748/#870/#873/#946에서 active/expired control ViewModel·Controller·Store·Policy regression, 직접 분 입력, active controls fresh guardian PIN 입력/확인, locale/contract tests, AccessibilityService active/expired instrumentation, setup/active/expired accessibility summary baseline이 current-head green으로 확인됐다. PR #897 이후 Parent Mode-origin Block 화면은 기존 `app_block_intercepted(block_source=parent_mode)`와 dedicated `parent_mode_block_intercepted(block_context=disallowed_app)`를 함께 남기며, PR #913 이후 `ParentModeSetupScreen` canonical `screen_view` coverage도 analytics/readback 선행 조건에 포함된다. PR #970 이후 source of truth는 run-relative `code-lane PR` 표현 대신 landed PR/merge commit 기준으로 유지하고, PR #980(`a0360ab6`) 이후 finished session의 `부모 모드 다시 시작` CTA/clear contract까지 repo-internal baseline에 포함한다. PR #1078(`10990435`) 이후 이미 종료된 session에 대한 연장/즉시 종료 재호출은 no-op으로 유지되어 재활성화와 중복 completion analytics를 만들지 않는다. 이 상태를 “active controls 구현 전”, “직접 설정 미구현”, “TalkBack baseline 미정의”, “PIN 없는 active 연장/종료 허용”, “dedicated block analytics 미구현”, “setup screen_view 미계측”, “종료 후 재시작 경로 없음”, “finished session 재호출 guard 없음”으로 되돌리지 않는다.
- 남은 manual/release boundary: release-candidate device UX spot-check, 실제 기기 screenshot/TalkBack 확인, release/tag/Play deploy 포함, GA4 Admin metadata/queryability, D+14/D+30 readback.

남은 범위는 MVP 전체 릴리스/실측 검증이다. 이미 반영된 repo-internal foothold와 analytics coverage를 “구현 전” 상태로 되돌리지 말고, 다음 실행 lane은 release-candidate device UX spot-check, 실제 기기 screenshot/TalkBack evidence, release/readback 경계를 이어 붙이는 방향으로 잡는다.

### 후속 별도 이슈 후보

- 원격 부모폰에서 아이 폰/태블릿 관리
- 가족 계정/기기 연결
- 원격 연장/해제 승인
- 자녀별 프로필/정책 템플릿
- 강한 anti-circumvention mode

### 12차 code-lane 설정 화면 UX — PIN 게이트 분리 · duration 휠

11차까지가 "무엇이 잠기는지, 왜 잠겼는지"를 말하게 만든 lane이라면, 이번 경계는 부모가 그 약속을 만드는
화면 자체를 다룬다. 실기기 확인에서 두 가지가 걸렸다: 비밀번호를 시간·앱 선택과 같은 페이지에서 받는 것이
어색하고, 시간 선택이 칩이라 같은 값이 화면에 세 번(헤더 라벨·선택된 칩·직접 입력 필드) 나와 있었다.

- 보호자 PIN 게이트 분리: PIN은 설정값이 아니라 폰이 손을 바꾸는 순간의 관문이라, 폼에서 빼고
  `ParentModeGuardianAction`(`Start`/`Extend`/`End`)이 여는 `ParentModeGuardianPinSheet` 한 곳에서만 받는다.
  `ParentModeSetupUiState.canRequestStart`(시간·앱만)와 `canAttemptStart`(거기에 PIN 확인)를 분리해 setup CTA는
  약속이 완성되면 활성화되고, PIN은 그 뒤 시트에서 묻는다. 시트를 열거나 닫을 때마다 입력값을 비워
  이전 시트에 남은 PIN이 다음 행동에 재사용되지 않게 한다.
- 진행 중 화면의 죽은 카드 제거: 연장/종료 버튼은 이제 같은 시트를 열기 때문에 항상 살아 있고, 종료된
  세션에서는 `보호자 인증` 카드를 비활성 상태로 그리는 대신 아예 그리지 않는다. 만료 화면에 남는 것은
  `parent_mode_expired_end_and_unlock` 하나뿐이다.
- duration 휠: `ParentModeDurationPicker`(시/분 2열, `Picker`/`rememberPickerState` 재사용)가 값이 사는 유일한
  자리다. 앱은 이미 수동 카운트다운 잠금을 `CountDownPicker` 휠로 받고 있었고, 부모 모드의 허용 시간도 같은
  종류의 값인데 혼자 칩+숫자 필드를 쓰고 있었다. 프리셋 칩(10/20/30/60)은 남되 값의 두 번째 사본이 아니라
  휠을 움직이는 바로가기다 — 칩을 누르면 휠이 remount되어 그 값으로 간다. 직접 입력 필드와
  `parent_mode_setup_duration_custom_label` / `..._custom_helper` 문자열은 삭제했다.
- 접히는 선 예산: 첫 배치(휠 5행 + 카드 순서 시간→앱)를 SM-G991N(1080×2400, density 480)에서 재보니 허용 시간
  카드가 콘텐츠 영역 `1714px`의 `957px`(56%)를 먹고, 허용 앱을 고르는 유일한 버튼이 `5px`만 남아 사실상
  접혀 있었다. 기본값과 프리셋이 있는 시간이 VOC의 본론인 허용 앱을 밀어낸 셈이라 셋을 되돌렸다:
  카드 순서를 앱 → 시간으로 바꾸고(히어로 문구가 이미 앱을 먼저 말한다), 휠을 5행 → 3행(`565px` → `339px`)으로
  줄이고, 카드 안에서는 프리셋 칩을 휠 위로 올렸다. 카드가 아래부터 잘릴 때 남아야 하는 건 휠이 아니라
  프리셋이기 때문이다. 재측정: 허용 앱 카드 전체와 `앱 선택 화면에서 조정`(`877..943`), 허용 시간 헤더와
  프리셋 칩(`1670..1726`)이 모두 접히는 선(`1935`) 위에 들어온다. 컨트롤 사용법을 설명하던
  `parent_mode_setup_duration_helper` 캡션은 삭제했다 — 설명이 필요한 컨트롤이라는 신호였다.
- 허용 앱 목록 상한: 목록은 편집기가 아니라 확인용이고 편집은 `앱 선택 화면에서 조정`이 맡으므로,
  `allowedAppsPreview(...)`가 앞의 3개만 남기고 나머지를 `parent_mode_setup_allowed_apps_overflow`
  (`외 %1$d개`)로 접는다. 상한이 없던 동안에는 앱을 고를수록 카드가 자라 아래 허용 시간 카드를 계속
  밀어냈다. 실기기 5개 선택 재측정: 허용 앱 카드가 `632..1732`로 고정되고 허용 시간 헤더(`1828..1884`)가
  접히는 선(`1935`) 위에 남는다.
- 회귀: `ParentModeSetupViewModelTest`의 duration 3건(`durationWheelStartsParentModeWithTheHourAndMinuteTheParentDialled`,
  `durationWheelCarriesHoursIntoTheStoredSessionMinutes`, `presetDurationReplacesWhateverTheWheelWasShowing`)과
  guardian sheet 4건(`theStartCtaOpensTheGuardianSheetInsteadOfStartingTheSessionOutright`,
  `confirmingTheGuardianSheetWithAMatchingPinStartsTheSessionAndClosesTheSheet`,
  `aMismatchedPinKeepsTheGuardianSheetOpenAndLeavesTheSessionAlone`,
  `dismissingOrReopeningTheGuardianSheetClearsTheTypedPin`,
  `theGuardianSheetRoutesExtendAndEndThroughTheSamePinGate`), `ParentModeSetupScreenAccessibilityTest`의
  폼/진행 중/만료/시트 baseline 6건.

여전히 남은 경계는 11차와 같다: 보호자 PIN은 저장되지 않으므로 시트로 옮긴 뒤에도 보안 강도는 그대로다.
시트는 "매번 새로 정한다"는 현재 동작을 더 정직하게 드러낼 뿐, PIN 해시 영속화는 별도 lane으로 남는다.

## Closing discipline

- 이 문서는 PR #519/#584/#748/#870/#873/#897/#913/#946/#970/#980/#1078 이후의 repo-internal foothold 상태를 반영한 source of truth다. 후속 docs sync나 code-lane PR은 acceptance 전체를 만족하지 못하면 계속 `Refs #471`를 사용한다.
- `Closes #471`는 부모 모드 entrypoint, setup/active/expired UI, fresh guardian PIN 확인 runtime flow, time expiry, Accessibility runtime 차단, privacy-safe analytics, setup `screen_view` coverage, QA evidence가 모두 구현·검증되고 release/manual/readback 경계까지 충족된 PR에서만 사용한다.
- GA4 Admin 등록, release/tag/Play deploy, release-candidate device UX/screenshot/TalkBack spot-check, 14일/30일 readback은 구현 완료 뒤의 외부/manual boundary로 별도 기록한다.

## Contract regression

문서 계약이 drift되지 않도록 아래 테스트를 사용한다.

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_parent_mode_contract -v
```

이 `parent-mode contract regression`은 `docs/PARENT_MODE_MVP.md`, analytics dictionary, GA4 runbook, product/metrics context, runtime QA checklist, docs AGENTS 링크가 함께 유지되는지 확인한다.
