# FCM Device Token / Registration Analytics Contract

이 문서는 issue #194의 문서·운영 계약 표면이다. 백엔드 API가 제거된 현재 Stopit에서 **FCM token 로컬 저장**과 **백엔드 device registration**을 혼동하지 않도록 실제 발생 이벤트, 남아 있는 legacy 계약, 검증 경계를 분리한다.

## 현재 코드 source of truth

| 책임 | 현재 source of truth | 의미 |
| --- | --- | --- |
| FCM token refresh entry point | `app/src/main/java/com/uiery/keep/service/KeepMessagingService.kt` | Firebase `onNewToken()`에서 새 token을 받아 저장 경로로 전달한다. |
| token 저장/analytics 순서 | `app/src/main/java/com/uiery/keep/DeviceTokenManager.kt` | token을 `LocalDeviceDataStore`에 저장하고 현재 analytics 이벤트를 기록한다. |
| analytics API/상수 | `app/src/main/java/com/uiery/keep/analytics/KeepAnalytics.kt` | 현재 발생 가능한 FCM token / backend-removed registration 이벤트만 노출한다. |
| Firebase analytics adapter | `app/src/main/java/com/uiery/keep/analytics/FirebaseKeepAnalytics.kt` | `KeepAnalytics` 호출을 Firebase event로 변환한다. |
| token 저장 regression | `app/src/test/java/com/uiery/keep/DeviceTokenManagerTest.kt` | 현재 저장 + event order 계약을 고정한다. |
| runtime wiring baseline | `app/src/androidTest/java/com/uiery/keep/service/KeepMessagingServiceIntegrationTest.kt` | stale token overwrite wiring을 Android runtime에서 확인한다. |

## 용어 계약

### FCM token local persistence

- Firebase가 발급한 token을 앱의 `LocalDeviceDataStore` / `PreferencesKey.FCM_TOKEN`에 저장하는 로컬 계약이다.
- 백업/복원 정책상 `fcm_token`은 기기·세션 종속 값이므로 복원하지 않는다.
- 복원 또는 새 기기에서는 token이 다시 발급/저장되어야 하며, 이 경로는 `KeepMessagingServiceIntegrationTest`가 자동 baseline으로 확인한다.

### Backend device registration

- 제거된 first-party backend에 기기 token을 등록하는 네트워크 파이프라인을 뜻한다.
- 현재 앱에는 first-party Retrofit/OkHttp backend API가 없고, device registration 성공/실패를 판정할 원격 호출도 없다.
- 따라서 현재 운영에서 `device_registration_succeeded` / `device_registration_failed`는 “실제로 성공/실패가 일어난다”는 의미로 해석하면 안 된다.

## 현재 실제 발생 가능한 이벤트

`DeviceTokenManager.saveDeviceToken(deviceToken)` 기준으로 현재 앱 런타임에서 발생 가능한 이벤트는 아래 세 종류다.

| 이벤트명 | 파라미터 | 발생 조건 | 운영 해석 |
| --- | --- | --- | --- |
| `fcm_token_captured` | 없음 | `saveDeviceToken()` 호출 후 항상 | Firebase token을 앱이 받았고 로컬 저장 경로에 진입했다. |
| `device_registration_attempted` | 없음 | `saveDeviceToken()` 호출 후 항상 | legacy registration funnel의 시작 marker다. 현재는 backend 호출 시도가 아니라 token 저장 경로 진입 marker에 가깝다. |
| `device_registration_skipped` | `reason` | `saveDeviceToken()` 호출 후 항상 | backend registration이 수행되지 않았다는 현재 정상 상태를 명시한다. |

현재 `device_registration_skipped.reason` 값은 아래 두 가지다.

| reason | 발생 조건 | 운영 해석 |
| --- | --- | --- |
| `missing_fcm_token` | 전달된 token이 blank | 저장 경로에 진입했지만 registration/token 운영 신뢰도가 낮다. Firebase callback 또는 test fixture 입력을 점검한다. |
| `backend_removed` | token이 blank가 아님 | 현재 정상 경로다. token은 로컬 저장됐지만 제거된 backend registration은 수행하지 않는다. |

## 제거된 legacy 계약

아래 이벤트는 backend device registration 파이프라인이 제거된 뒤 더 이상 현재 코드 표면에서 API/event constant로 노출하지 않는다.

| 이벤트명 | 현재 상태 | 해석 |
| --- | --- | --- |
| `device_registration_succeeded` | API/event constant 제거됨 | backend registration이 재도입되기 전까지 성공 이벤트로 해석하지 않는다. |
| `device_registration_failed` | API/event constant 제거됨 | backend registration이 재도입되기 전까지 실패율 지표로 해석하지 않는다. |

운영 분석에서 위 두 이벤트가 GA4에 잡히면 먼저 앱 버전, 과거 release, manual/test event, 또는 legacy client 유입 가능성을 분리한다. 현재 코드 기준의 건강도 판단은 `fcm_token_captured`와 `device_registration_skipped.reason`을 우선한다.

## GA4 / 대시보드 해석 가드레일

- `fcm_token_captured`는 “푸시 token을 앱이 받았다”는 신호이지, backend 등록 성공이나 실제 push 수신 성공을 보장하지 않는다.
- `device_registration_attempted`는 현재 backend network attempt가 아니다. legacy funnel 이름 때문에 success/fail 전환율처럼 해석하지 않는다.
- 현재 정상 token refresh는 `device_registration_skipped(reason=backend_removed)`까지 함께 기록될 수 있다. 이 값만 보고 장애로 분류하지 않는다.
- `device_registration_skipped(reason=missing_fcm_token)` 비중이 높아지면 token refresh 입력, Firebase callback, test fixture, restore/reset 경로를 점검한다.
- 실제 push delivery 문제는 FCM 콘솔/메시징 evidence와 별도로 판단한다. 앱 analytics만으로 delivery 성공을 단정하지 않는다.

## 검증 포인트

### 문서/계약 점검

```bash
cd <repo-root>
rg -n 'fcm_token_captured|device_registration_|backend_removed|missing_fcm_token' \
  app/src/main/java app/src/test/java app/src/androidTest/java docs --glob '*.{kt,md}'
```

확인 기준:

- 실제 production call site가 `DeviceTokenManager.saveDeviceToken()` 경로인지 확인한다.
- `device_registration_succeeded` / `device_registration_failed`가 현재 코드 표면에 다시 추가되지 않았는지 확인한다.
- 운영 문서에서 `FCM token 저장`과 `backend device registration`을 분리해 설명하는지 확인한다.

### 현재 token 저장 regression

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests com.uiery.keep.DeviceTokenManagerTest
```

### Android runtime wiring baseline

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepMessagingServiceIntegrationTest
```

## 현재 closure-pass 상태와 남은 경계

문서/운영 계약은 docs-lane에서 가능한 범위까지 정리됐고, code lane에서 legacy success/fail API 제거 결정을 반영했다.

- PR #215가 `docs/FCM_DEVICE_REGISTRATION_CONTRACT.md`, backup/restore 문서, AGENTS 근접 문서에 FCM token 로컬 저장 vs 제거된 backend registration 경계를 추가했다.
- PR #154가 merge되면서 `docs/ANALYTICS_EVENT_DICTIONARY.md`와 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md`의 canonical analytics 표도 같은 해석으로 동기화됐다.
- 현재 문서 기준으로 `fcm_token_captured`, `device_registration_attempted`, `device_registration_skipped(reason=backend_removed|missing_fcm_token)`만 살아 있는 런타임 운영 신호로 본다.
- `device_registration_succeeded` / `device_registration_failed`는 API/event constant까지 제거된 legacy 이벤트이며, backend registration 재도입 전에는 성공/실패 제품 지표로 해석하지 않는다.

issue #194를 완전히 닫으려면 이제 배포 후 실제 GA4/운영 화면에서 legacy success/fail 이벤트가 새로 유입되지 않는지 관측하는 후속만 남는다.

- `KeepAnalyticsEvent.ACTIVE_DEVICE_REGISTRATION_EVENTS`와 `FirebaseKeepAnalyticsTest`가 현재 런타임 이벤트 집합을 고정한다.
- `DeviceTokenManager`는 계속 token 저장 + backend removed skip marker를 기록한다.
- backend registration을 다시 도입할 때만 별도 이슈/PR에서 success/fail 이벤트명을 새 계약으로 재추가한다.

## PR / issue 기록 문구

#194를 code lane에서 마무리하는 PR에서는 아래처럼 경계를 명확히 적는다.

```md
Closes #194

이번 PR은 backend registration 제거 이후 남아 있던 `device_registration_succeeded` / `device_registration_failed` API·event constant·analytics test 고정을 제거하고, 현재 런타임 이벤트 집합을 `ACTIVE_DEVICE_REGISTRATION_EVENTS`로 고정했다. 배포 후 GA4에서 legacy success/fail 이벤트가 새로 보이면 과거 앱 버전/manual event/새 call site 재도입 여부를 먼저 분리한다.
```
