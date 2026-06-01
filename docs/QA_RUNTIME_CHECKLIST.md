# Stopit Runtime QA Checklist

이 문서는 리시버/서비스 계층의 Android 런타임 검증을 반복 가능하게 만들기 위한 수동 QA 기준이다.

범위:
- `BootReceiver`
- `RoutineAlarmReceiver`
- `KeepAccessibilityService`
- 긴급해제 만료/차단 복귀
- release 전 device/emulator 검증 순서

백업/복원 정책 자체는 `docs/BACKUP_RESTORE_POLICY.md`를 source of truth로 본다. 현재 정책은 `keep-database`만 복원하고 `keep-datastore`는 통째로 제외하는 보수적 계약이다. 이 문서는 복원 이후에도 receiver/service/runtime 상태가 안전하게 동작하는지 확인하는 실행 체크리스트다.

비범위:
- Room migration 세부 검증
- Play Console 수동 프로모션 절차
- 대규모 instrumented test 구현

> 현재 저장소의 `androidTest` 자동화는 release 전체를 대체하지는 않지만, 기본 Android CI focused runtime smoke가 이미 핵심 런타임 계약을 자동 검증한다: `StopitReleaseSmokeTest`(앱 기동 smoke), `BackupRestoreRuntimeResetIntegrationTest`(복원 후 reset-only state 미복원), 네 개의 focused `ReceiverRuntimeIntegrationTest` 메서드(boot/package-replaced 재수화 + 루틴 시작 재예약), 별도 `POST_NOTIFICATION ignore` receiver fallback notice 메서드, `EmergencyUnlockExpiryIntegrationTest`(긴급해제 만료 cleanup + 재차단 대상), `KeepMessagingServiceIntegrationTest`(stale FCM token overwrite), `KeepAccessibilityServiceIntegrationTest`(cross-app foreground 차단 + emergency unlock 우회 safety). 이 체크리스트는 그 자동화가 아직 덮지 못하는 cold boot, uninstall 방지, 실제 사용자 앱 조합별 foreground 전환 같은 수동 증거를 release 전에 반복하기 위한 최소 기준이다.

## 1. 사전 준비

### 로컬 필수 조건

- Android Studio 또는 Android SDK/ADB 사용 가능
- `local.properties`가 현재 worktree에 존재
- 필요 flavor의 `google-services.json`이 현재 worktree에 복원되어 있음
- 테스트 기기 또는 에뮬레이터 1대 이상 연결

### 권장 사전 명령

```bash
cd <repo-root>
./gradlew -q help --task :app:testDevDebugUnitTest
./gradlew -q help --task :app:connectedDevDebugAndroidTest
```

### 자동화 기본선

작은 코드 변경이 함께 있는 PR이라면 최소한 아래 중 하나를 같이 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest
./gradlew :app:connectedDevDebugAndroidTest
```

- `:app:testDevDebugUnitTest`: 빠른 JVM 회귀 확인
- `:app:connectedDevDebugAndroidTest`: device/emulator 기반 Android 통합 검증
- 로컬 prerequisite 부족으로 instrumentation을 못 돌리면, 막힌 이유를 PR 본문에 명시하고 아래 수동 QA evidence를 남긴다.

### 홈 타이머 CTA duration baseline

issue #187 계열 PR에서는 홈 타이머 바텀시트가 실제 `현재 시각 -> 목표 시각` 차이와 같은 값을 CTA에 표시하는지 JVM 계약 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.feature.home.component.TimerTimeContractTest'
```

검증 범위:
- `10:50 -> 11:10`처럼 시/분 경계가 걸린 20분 타이머가 `1시간 20분`으로 과장되지 않는다.
- `23:50 -> 00:10`처럼 자정을 넘는 타이머가 20분으로 계산된다.
- 목표 시각이 현재 시각과 같으면 버튼 활성화에 쓰는 duration이 `0시간 0분`으로 남는다.
- 12시간제 picker는 `0시` 대신 `12시`를 표시하고, AM/PM `12` 선택을 각각 `00:xx` / `12:xx`로 변환한다.

수동 QA가 필요하면 홈 → 시간 설정 → 타이머 탭에서 위 경계 시각을 맞춘 뒤 CTA 문구와 실제 잠금 종료 시각이 같은 duration을 가리키는지 기록한다.

### develop/main 기본 CI gate

`Android CI`는 release 전용 `release-qa.yml`보다 가벼운 기본 PR gate로 아래를 자동 실행한다.

- `./gradlew :app:testDevDebugUnitTest`
- `./gradlew :app:lintDevDebug`
- `./gradlew :app:assembleProdDebug`
- focused runtime smoke class/method set:
  - `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - `com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForPackageAndClockChangeActions`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm`
  - `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine`
  - `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`
  - `com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
  - `com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest`
- separate host-side appops run:
  - `./gradlew :app:installDevDebug`
  - `adb shell appops set com.uiery.keep POST_NOTIFICATION ignore`
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`

이 gate는 develop/main PR 단계에서 lint·핵심 runtime 계약을 먼저 막는 역할이다.

- `StopitReleaseSmokeTest`: 앱 기동 + Compose navigation host smoke
- `BackupRestoreRuntimeResetIntegrationTest`: 복원된 Room + 비어 있는 DataStore shape에서 reset-only state 미복원
- focused `ReceiverRuntimeIntegrationTest`: Boot/package-replaced/time/timezone 변경 후 Room 재수화, 단일·다중 요일 루틴 exact alarm 재예약, 루틴 시작 재예약, notification-denied fallback notice contract
- `EmergencyUnlockExpiryIntegrationTest`: 긴급해제 만료 state cleanup + 재차단 대상 판정
- `KeepMessagingServiceIntegrationTest`: FCM token regeneration storage wiring
- `KeepAccessibilityServiceIntegrationTest`: 실제 AccessibilityService bind 후 cross-app foreground 전환에서 `BlockActivity` 진입과 emergency unlock 우회 safety 계약

exact alarm 권한 deny/allow 전환과 release-only remaining connected suite는 여전히 release/hotfix 대상 `Android Release QA`가 담당한다.

### DevTool production graph baseline

DevTool은 `Device ID`/`FCM Token` 같은 내부 진단값을 표시하므로 production graph에 등록되지 않아야 한다. dev/debug 진단 접근은 유지하되, prod flavor에서는 debug/release 여부와 무관하게 route 등록 자체가 막혀야 한다.

Issue #208 계열 PR은 아래 JVM 정책 테스트와 prod-like artifact build를 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.KeepAppNavigationPolicyTest"
./gradlew :app:assembleProdDebug
```

검증 기준:
- `shouldRegisterDevToolRoute(flavor = "dev", isDebug = true)`만 `true`다.
- `prodDebug`/`prodRelease` 조합에서는 DevTool route가 `NavHost`에 등록되지 않는다.
- prod 사용자 화면에서 `Device ID`/`FCM Token` 진단값으로 이동할 수 있는 메뉴/graph 경로가 남지 않는다.

### Android 공식 testing skill 기반 UI smoke baseline

Android skills가 설치된 환경에서는 `testing-setup`과 `android-cli` skill을 먼저 읽고 QA 범위를 잡는다.

- `/Users/uiel/.agents/skills/testing-setup/SKILL.md`
- `/Users/uiel/.agents/skills/android-cli/SKILL.md`
- 운영 문서: `docs/ANDROID_SKILLS_TESTING_QA.md`

release/hotfix PR은 `Release instrumentation QA`에서 아래 순서로 release runtime gate를 실행한다. 세부 단계 source of truth는 `.github/workflows/release-qa.yml`과 `docs/ops/stopit/release-context.md`이며, 이 문서는 그 순서를 사람이 반복 실행하기 쉬운 checklist 형태로 풀어쓴 것이다.

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.StopitReleaseSmokeTest,com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#manifestRegistersBootReceiverForMyPackageReplaced,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest,com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep POST_NOTIFICATION ignore
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine
```

즉, release candidate baseline은 `focused UI smoke -> exact alarm deny(4개) -> exact alarm allow(1개) -> remaining connected suite -> notification-denied fallback` 순서다. exact alarm/notification appops 전환은 target app 프로세스를 죽일 수 있으므로, 권한 상태 변경은 테스트 메서드 안이 아니라 **host ADB 명령 → focused instrumentation 실행** 순서로 유지해야 한다.

### receiver/service instrumentation baseline

issue #27 계열 PR에서는 아래 focused Android 통합 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest
```

- `bootReceiverRehydratesStoredRoutinesFromRoomAndSchedulesAlarm`
- `manifestRegistersBootReceiverForPackageAndClockChangeActions`
- `timeChangedRestoresRoutinesFromRoomAndSchedulesAlarm`
- `timezoneChangedRestoresMultiDayRoutinesFromRoomAndSchedulesAlarms`
- `packageReplacedRestoresRoutinesFromRoomAndSchedulesAlarm`
- `routineAlarmReceiverShowsNotificationRehydratesDataStoreAndReschedulesEnabledRoutine`
- `routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine`

이 baseline은 BootReceiver/RoutineAlarmReceiver의 핵심 재수화·재예약 contract를 검증한다. 특히 `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`까지 포함해 앱 업데이트나 기기 wall-clock/timezone 변경 후에도 활성 루틴 복구 경로가 manifest와 런타임 로직 양쪽에서 유지되는지 확인한다. 시간/시간대 변경 경로는 exact alarm 권한 회수(#137/#149)와 별개로, 사용자가 설정한 로컬 시각 기준으로 단일 요일·다중 요일 루틴 `PendingIntent`가 다시 생성되는지를 본다. 또한 Android 13+에서 `POST_NOTIFICATION`이 꺼진 상태에서도 루틴 시작이 조용히 사라지지 않고 앱 내 fallback notice로 이어지는지 확인한다. 다만 protected broadcast 기반 실제 cold boot와 AccessibilityService의 cross-app 차단 진입은 아래 수동 시나리오 evidence가 여전히 필요하다.

`POST_NOTIFICATION` deny focused test는 exact alarm appops와 비슷하게 **호스트 ADB/appops에서 먼저 상태를 바꾸고 그 다음 focused instrumentation을 실행**해야 한다. 테스트 메서드 안에서 notification appops를 바꾸면 target process가 죽어 flaky/crash가 날 수 있다.

추가 수동 확인 포인트: 홈의 category/time 바텀시트가 이미 열린 상태에서 루틴이 시작된 경우, 시트가 열려 있을 때는 fallback notice가 바로 보이지 않아도 된다. 대신 시트를 닫은 직후 홈 snackbar로 루틴 시작 안내가 **정확히 한 번** 노출되어야 하며, 같은 홈 복귀에서 중복 반복되면 안 된다.

```bash
cd <repo-root>
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep POST_NOTIFICATION ignore
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest#routineAlarmReceiverWithoutPostNotificationsPermissionQueuesFallbackNoticeRehydratesDataStoreAndReschedulesEnabledRoutine
adb shell appops set com.uiery.keep POST_NOTIFICATION allow
```

### exact alarm permission baseline

issue #77 / #137 계열 PR에서는 Android 12+ exact alarm 권한 거절/허용 경로를 각각 분리해서 남긴다. `appops set`은 target app 프로세스를 죽일 수 있으므로, 권한 상태 변경은 테스트 메서드 안이 아니라 **host ADB 명령 → focused instrumentation 실행** 순서로 기록한다.

```bash
cd <repo-root>
./gradlew :app:installDevDebug

# 거절 상태: 활성 루틴이 조용히 성공 상태로 남지 않아야 한다.
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#addRoutineWithoutExactAlarmPermissionStoresDisabledRoutineAndRequestsPrompt

./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#bootReceiverWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent

./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#packageReplacedWithExactAlarmPermissionDeniedDisablesEnabledRoutinesAndLeavesNoPendingIntent

./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.receiver.ReceiverExactAlarmPermissionIntegrationTest#routineAlarmReceiverWithExactAlarmPermissionDeniedDisablesRoutineAndLeavesNoNextPendingIntent

# 허용 상태: 동일 경로에서 실제 PendingIntent 예약이 생겨야 한다.
./gradlew :app:installDevDebug
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM allow
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.routine.RoutineExactAlarmPermissionIntegrationTest#enablingRoutineWithExactAlarmPermissionSchedulesAlarm
```

- 거절 경로 검증 범위:
  - `RoutineBottomSheetViewModel` 저장 시 side effect로 권한 안내를 띄우는지
  - DB에 저장된 루틴이 `enabled=false`로 안전하게 내려가는지
  - 동일 루틴 ID의 `PendingIntent`가 남지 않는지
  - `BootReceiver`가 부팅/패키지 교체 복구 중 exact alarm 재예약 실패를 만나도 해당 루틴을 `enabled=false`로 내리고 `HAS_SHOWN_ALARM_PERMISSION=false`로 되돌리는지
  - `MY_PACKAGE_REPLACED` 경로에서도 동일한 downgrade/no-pending-intent 계약이 유지되는지
  - `RoutineAlarmReceiver`가 루틴 시작 알림은 현재 시점에 계속 보여주되, 다음 exact alarm 재예약 실패 시 루틴을 `enabled=false`로 내리고 다음 `PendingIntent`를 남기지 않는지
- 허용 경로 검증 범위:
  - `RoutineViewModel.changeEnabled(...)`가 루틴을 다시 `enabled=true`로 올리는지
  - exact alarm `PendingIntent`가 실제로 예약되는지

### exact alarm permission 수동 evidence 템플릿

Android 12+ 실기기/에뮬레이터에서 추가 스크린샷 evidence가 필요하면 아래 형식으로 남긴다.

```md
## Exact alarm permission evidence
- Device/Emulator:
- Android version:
- Variant:
- Routine name / id:
- Permission state before save: allow / deny
- Commands:
  - `adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny|allow`
  - `adb shell dumpsys alarm | grep com.uiery.keep`
- Observed UI:
  - 권한 안내 바텀시트 노출 여부
  - 루틴 enabled/disabled 상태
- PendingIntent evidence:
- Notes:
```

### FCM token 재생성 baseline

issue #68 계열 PR에서는 아래 focused Android 통합 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepMessagingServiceIntegrationTest
```

- `persistNewTokenForContext_overwritesExistingStoredTokenViaEntryPoint`
- 검증 범위: `KeepMessagingService -> EntryPointAccessors -> DeviceTokenManager -> DataStore` 저장 wiring
- 이 baseline은 실제 FCM 서버 콜백을 대체하지 않지만, 새 기기/복원 후 토큰 재생성 시 앱 내부 저장 경로가 끊기지 않았는지 release 전에 반복 검증할 수 있게 한다.

### FCM token 재생성 수동 evidence 템플릿

새 기기/복원 시나리오에서 자동화 외 evidence가 필요하면 아래 형식으로 남긴다.

```md
## FCM token regeneration evidence
- Device/Emulator:
- Variant:
- Previous stored token:
- Trigger: fresh install / device transfer / restore after backup
- Commands:
  - `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
- Result token after regeneration:
- Notes:
```

### 긴급해제 만료 scriptable baseline

issue #67 계열 PR에서는 아래 focused Android 통합 테스트를 기본 evidence로 남긴다.

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest
```

- `handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage`
- 검증 범위: 만료 시각 도달 시 `EmergencyUnlockState`와 DataStore의 `EMERGENCY_UNLOCK_*` state를 제거하고, 전면 앱이 만료된 예외 앱이면 재차단 대상으로 되돌리는지
- 이 baseline은 실제 cross-app Accessibility 진입 전체를 대체하지는 않지만, 가장 위험한 만료 후 우회 지속 회귀를 device/emulator에서 반복 가능하게 고정한다.

### receiver/service QA용 권장 focused JVM baseline

issue #27 계열처럼 receiver/service runtime 리스크를 다루지만 `connectedDevDebugAndroidTest`까지 즉시 돌리기 어려운 PR이라면, 최소한 아래 focused JVM baseline은 함께 남긴다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.receiver.RoutineReceiverPolicyTest" \
  --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest"
```

- `RoutineReceiverPolicyTest`: 저장된 루틴 JSON decode, enabled routine 재예약 선택 로직의 회귀를 빠르게 잡는다.
- `EmergencyUnlockPolicyTest`: 긴급해제 만료/허용 판단의 순수 로직 회귀를 빠르게 잡는다.
- 이 baseline은 Android runtime 자체를 대체하지 않는다. 아래 시나리오 evidence 또는 `:app:connectedDevDebugAndroidTest`와 함께 해석한다.

### 공통 evidence 수집 팁

가능하면 각 시나리오 전후로 아래를 같이 남긴다.

```bash
adb shell dumpsys alarm | grep com.uiery.keep
adb logcat -d | grep -E "RoutineAlarmReceiver|BootReceiver|KeepAccessibilityService|EmergencyUnlock"
```

- `dumpsys alarm`: receiver 이후 다음 알람/루틴 재예약 여부를 남길 때 유용하다.
- `logcat`: 런타임 크래시/경고를 함께 남길 때 유용하다.
- 민감 로그 금지: `RoutineModel.toString()`처럼 루틴명, 시간대, 반복 요일, 차단 앱 package를 한 번에 노출하는 raw 모델 로그를 남기지 않는다. 토큰, device id, 긴급해제 상태도 raw logcat evidence에 남기기 전에 마스킹한다.
- 로그 태그나 출력은 빌드에 따라 충분하지 않을 수 있으므로, 스크린샷/시각/루틴 이름 같은 사용자 관찰 evidence를 같이 보관한다.

## 2. BootReceiver 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/receiver/BootReceiver.kt`
- `app/src/main/AndroidManifest.xml`

### 목적

부팅 후 저장된 루틴이 다시 스케줄링되어야 한다. 동일한 복구 계약은 앱 업데이트로 패키지가 교체된 뒤(`MY_PACKAGE_REPLACED`)에도 유지되어야 한다.

### 시나리오 A — cold boot / reboot

1. 루틴이 1개 이상 활성화된 상태를 만든다.
2. 앱을 완전히 종료한다.
3. 기기를 재부팅하거나, 에뮬레이터를 cold boot한다.
4. 부팅 후 앱을 열지 않은 상태에서도 다음 루틴 알림/스케줄이 유지되는지 확인한다.

> `BOOT_COMPLETED`는 protected broadcast라서 `adb shell am broadcast ...`만으로 안정적으로 재현되지 않을 수 있다. BootReceiver 검증은 실제 reboot/cold boot를 기준으로 남긴다.

### 시나리오 B — 앱 업데이트 후 복구

1. 활성 루틴 1개 이상을 만든다.
2. 업데이트 전 `adb shell dumpsys alarm | grep com.uiery.keep`로 예약 상태를 남긴다.
3. 같은 variant를 `adb install -r <apk>`로 덮어쓴다.
4. 업데이트 직후 앱을 열지 않은 상태에서 다음 루틴 예약이 유지되거나 즉시 재복구되는지 확인한다.
5. 필요하면 앱 실행 후 홈/루틴 화면에서 상태가 초기화되지 않았는지 확인한다.

### 확인 포인트

- [ ] `BOOT_COMPLETED` 이후 앱 크래시가 없다.
- [ ] `MY_PACKAGE_REPLACED` 이후에도 활성 루틴이 사라지지 않는다.
- [ ] 다음 루틴 시각에 맞는 알림/동작이 다시 예약된다.
- [ ] 재부팅/업데이트 직후 열어본 홈/루틴 화면에서 루틴 상태가 비정상으로 초기화되지 않는다.

### 실패 시 남길 evidence

- 기기/에뮬레이터 정보
- 재부팅 또는 업데이트 전후 루틴 이름/시간
- 전후 `adb shell dumpsys alarm | grep com.uiery.keep` 차이
- logcat 핵심 라인
- 실제 누락된 알림 또는 스케줄 증상

## 3. RoutineAlarmReceiver 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/receiver/RoutineAlarmReceiver.kt`

### 목적

루틴 시작 시 알림을 띄우고, 활성 루틴이면 다음 주차로 다시 예약해야 한다.

### 시나리오

1. 가까운 미래 시각에 반복 루틴을 만든다.
2. 대상 앱이 선택되어 있는지 확인한다.
3. 알림 시각까지 대기하거나 테스트 시간을 앞당겨 수신을 유도한다.
4. 알림 수신 직후 루틴이 다음 주차 기준으로 다시 예약되는지 확인한다.

권장 준비:

```bash
adb shell dumpsys alarm | grep com.uiery.keep
```

수신 전후의 예약 상태를 비교해 다음 회차가 실제로 다시 등록되었는지 남긴다.

### 확인 포인트

- [ ] 루틴 시작 알림이 정확한 루틴 이름으로 노출된다.
- [ ] 루틴이 enabled 상태면 다음 회차가 다시 예약된다.
- [ ] 루틴이 disabled 상태면 재예약되지 않는다.
- [ ] receiver 실행 후 중복 알림이 연속으로 뜨지 않는다.

### 실패 시 남길 evidence

- 루틴 ID/이름
- enabled 여부
- 기대한 알림 시각 vs 실제 시각
- 수신 전후 `adb shell dumpsys alarm | grep com.uiery.keep` 출력 차이
- 재예약 여부 스크린샷 또는 로그

### exact alarm permission 확인 포인트 (Android 12+)

1. exact alarm 권한을 `deny`로 둔 상태에서 활성 루틴 저장 또는 enable 시도를 한다.
2. 권한 안내 UI가 즉시 노출되는지 확인한다.
3. 루틴이 화면/DB 기준 `enabled=false`로 남고, `dumpsys alarm`에도 새 예약이 생기지 않는지 확인한다.
4. exact alarm 권한을 `allow`로 변경한다.
5. 같은 루틴을 다시 enable 하거나 새 활성 루틴을 저장한다.
6. 이번에는 `enabled=true` 상태와 실제 예약이 함께 생기는지 확인한다.

체크리스트:

- [ ] 권한 거절 상태에서 활성 루틴이 "성공처럼 보이지만 실제 미예약" 상태로 남지 않는다.
- [ ] 권한 거절 상태에서 사용자가 원인을 UI로 인지할 수 있다.
- [ ] 권한 허용 후에는 동일 루틴이 정상적으로 다시 예약된다.

## 4. KeepAccessibilityService 차단 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt`

### 목적

접근성 서비스가 저장된 잠금 상태와 루틴 상태를 반영해 실제 차단을 수행해야 한다.

### 현재 자동화 baseline

이 영역은 현재 두 층으로 evidence를 쌓는다.

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests 'com.uiery.keep.service.KeepAccessibilityServiceBlockDecisionTest'
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.KeepAccessibilityServiceIntegrationTest
```

- `KeepAccessibilityServiceBlockDecisionTest`: manual keep / timed lock / routine / duplicate / emergency unlock 우회 판단을 순수 JVM 회귀로 빠르게 고정한다.
- `KeepAccessibilityServiceIntegrationTest`: 실제 AccessibilityService bind 이후 cross-app foreground 전환에서 차단 Activity 진입 여부를 검증하려는 focused runtime harness다.
- 이 harness는 `setUp()`에서 접근성 서비스의 초기 활성 상태를 저장하고, `tearDown()`에서 **원래 꺼져 있던 경우 다시 disabled 상태로 원복**해야 한다. 후속 instrumentation/수동 QA가 접근성 서비스 잔여 상태에 오염되지 않도록 이 cleanup 계약을 유지한다.
- 현재 Android 15 emulator baseline은 실제 bind 후 두 핵심 시나리오를 반복 가능하게 검증한다: `selectedAppWithManualKeep_launchesBlockActivity` 와 `emergencyUnlockActive_keepsSelectedAppForegroundInsteadOfLaunchingBlockActivity`.

### Android 15 emulator instrumentation 메모

`KeepAccessibilityServiceIntegrationTest`를 Android 15 emulator에서 돌릴 때는 "토글은 켜졌지만 서비스 bind가 실제로 일어났는지"를 설정 값만 보고 추정하지 말고 아래 4가지를 같이 남긴다. 현재 baseline은 이 관측을 assertion message에 포함한 상태에서 실제 bind 성공까지 확인되도록 보강되었다.

```bash
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -n 'Bound services\|Enabled services\|Binding services\|Crashed services' -A1 -B1
adb logcat -d | grep -E 'KeepAccessibilityService|TestRunner|IPCThreadState|frozen process'
```

최근 qa-lane에서는 초기 run에서 `enabled_accessibility_services` 반영 뒤에도 `Bound services:{}` 상태와 `IPCThreadState: Sending oneway calls to frozen process.`가 보이는 bind 경계를 먼저 고정했고, 이후 harness를 보강해 **첫 테스트에서 실제 bind 성공, 후속 테스트에서는 연결 플래그를 유지한 채 emergency unlock safety 시나리오까지 연속 검증**하도록 안정화했다. 다시 유사 실패가 재발하면 PR/이슈 보고에는 **토글 반영과 실제 service bind를 분리해서** 적고, instrumentation assertion/message에도 동일 진단 정보를 남긴다.

### 시나리오 A — 수동 잠금

1. 접근성 권한을 켠다.
2. 차단 대상 앱을 1개 이상 선택한다.
3. 수동 잠금을 활성화한다.
4. 대상 앱을 연다.

확인:
- [ ] `BlockActivity`가 즉시 표시된다.
- [ ] 비대상 앱에서는 차단이 발생하지 않는다.
- [ ] 같은 앱 재진입 시 과도한 중복 차단/깜빡임이 없다.

### 시나리오 B — 시간 잠금

1. 가까운 미래까지 유지되는 timed lock을 설정한다.
2. 잠금 예약 직후, 아직 Lock 화면 종료 시점이 오기 전에 history/누적 시간이 증가하지 않았는지 확인한다.
3. 잠금 중 대상 앱을 연다.
4. 잠금 종료 후 같은 앱을 다시 연다.
5. 종료 후 history 상세와 누적 시간이 실제 종료된 세션 기준으로 한 번만 증가했는지 확인한다.

확인:
- [ ] 예약 직후에는 `lock_history`, `TOTAL_BLOCK_TIME`, `LONG_BLOCK_TIME`가 완료 세션처럼 선반영되지 않는다.
- [ ] 잠금 시간 내에는 차단된다.
- [ ] 잠금 만료 후에는 정상 진입된다.
- [ ] 만료 직전/직후에 차단 상태가 뒤집히는 이상 동작이 없다.
- [ ] 만료 후에는 home timer 세션이 `isRoutine=false`로 한 번만 기록되고 duration은 실제 시작~종료 구간과 일치한다.

자동/scriptable baseline:

```bash
cd <repo-root>
./gradlew :app:testDevDebugUnitTest \
  --tests "com.uiery.keep.feature.home.HomeViewModelActivationAnalyticsTest.lockTimeDoesNotPreRecordFutureTimerSessionInHistoryLedger" \
  --tests "com.uiery.keep.feature.lock.LockViewModelTest.completedHomeTimerRecordsHistoryLedgerAtLockCompletion"
```

### 시나리오 C — 루틴 차단

1. 현재 요일/시간에 활성화되도록 루틴을 만든다.
2. 대상 앱을 연다.

확인:
- [ ] 루틴 활성 구간에서만 차단된다.
- [ ] 루틴 비활성 구간에서는 차단되지 않는다.

### 시나리오 D — 다중 활성 루틴 겹침

1. 현재 시점에 동시에 활성화되도록 루틴 2개 이상을 만든다.
   - 예시:
     - 루틴 A: `com.instagram`, `com.youtube`
     - 루틴 B: `com.youtube`, `com.discord`
2. 접근성 차단이 켜진 상태에서 각 대상 앱을 순서대로 연다.
3. Lock 화면의 루틴 안내 문구와 긴급해제 바텀시트의 대상 앱 목록을 확인한다.
4. 루틴 종료 또는 긴급해제 후 잠금 기록을 확인한다.

확인:
- [ ] Lock 화면이 첫 번째 루틴 이름 하나만 잘못 보여주지 않고, 단일 활성 루틴이면 이름을, 다중 활성 루틴이면 개수 기반 문구를 보여준다.
- [ ] 실제 차단 대상 앱 집합이 활성 루틴들의 합집합(`com.instagram`, `com.youtube`, `com.discord`)과 일치한다.
- [ ] 긴급해제 대상 앱 목록이 첫 번째 루틴 기준으로 잘리지 않고 실제 차단 대상과 동일하다.
- [ ] 루틴 종료 후 `lock_history`의 `lockedApps`가 실제 차단 대상 합집합과 일치한다.
- [ ] 공통 앱(`com.youtube`)은 중복 없이 한 번만 취급된다.

권장 evidence:

```bash
adb logcat -d | grep -E "KeepAccessibilityService|LockViewModel|EmergencyUnlock"
```

- 가능하면 Lock 화면 스크린샷, 긴급해제 바텀시트 스크린샷, 종료 후 잠금 기록 스크린샷을 한 세트로 남긴다.

## 5. 긴급해제 만료 검증

관련 코드:
- `app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt`
- `app/src/main/java/com/uiery/keep/service/EmergencyUnlockState.kt`

### 목적

긴급해제가 활성 앱에는 일시적으로 통과를 허용하되, 만료 후에는 차단이 복구되어야 한다.

### 시나리오

1. 차단 중인 앱에서 긴급해제를 실행한다.
2. 긴급해제 유효 시간 동안 대상 앱을 사용한다.
3. 만료 시각이 지난 뒤 같은 앱을 다시 전면으로 가져온다.

자동/scriptable baseline:

```bash
cd <repo-root>
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest
```

- 위 baseline은 DataStore + `EmergencyUnlockState` + 재차단 대상 판정 contract를 실제 device/emulator에서 검증한다.
- 실제 blocked third-party app foreground 전환까지 포함한 end-to-end Accessibility 진입은 아래 수동 evidence로 따로 남긴다.

### 확인 포인트

- [ ] 긴급해제 유효 시간 동안 대상 앱이 차단되지 않는다.
- [ ] 만료 후 다시 앱 전면 진입 시 차단이 복구된다.
- [ ] 만료 후 데이터가 남아 차단이 계속 우회되지 않는다.
- [ ] 긴급해제와 무관한 다른 대상 앱은 계속 차단된다.

## 6. Backup / restore 후 런타임 상태 검증

관련 문서:
- `docs/BACKUP_RESTORE_POLICY.md`

### 목적

기기 이전/클라우드 복원 뒤에도 이전 기기의 DataStore 기반 잠금/긴급해제/리뷰/토큰 상태가 그대로 살아나지 않아야 한다.

### 시나리오

1. 기존 기기에서 아래를 모두 만든다.
   - 차단 앱 선택
   - 수동 잠금 또는 timed lock 활성화
   - 긴급해제 설정 변경
   - 가능하면 긴급해제 1회 실행
2. 백업/기기 이전 수행
3. 새 기기에서 앱 실행 직후 대상 앱과 루틴 화면을 확인한다.

### 확인 포인트

- [ ] 루틴은 필요 시 복원되지만, DataStore 기반 현재 잠금 상태는 그대로 살아나지 않는다.
- [ ] boot 또는 routine alarm 재진입 후 복원된 Room routine이 필요 시 비권위 `PreferencesKey.ROUTINES` 캐시로 다시 채워지더라도, 후속 스케줄링/차단 판단의 source of truth는 계속 Room이다.
- [ ] 이전 기기의 긴급해제 진행 상태가 복원되어 차단이 계속 우회되지 않는다.
- [ ] 선택 앱 목록/긴급해제 설정은 새 기기 기준으로 다시 설정해야 하는 상태다.
- [ ] 리뷰 프롬프트/토큰/세션성 플래그가 복원 직후 부자연스럽게 이어지지 않는다.

### 자동 baseline 명령

- `./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest,com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest,com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest,com.uiery.keep.service.KeepMessagingServiceIntegrationTest`

자동 baseline이 확인하는 것:
- `BackupRestoreRuntimeResetIntegrationTest`: restored-device shape(복원된 Room + 비어 있는 DataStore)에서 Boot/Routine alarm 진입 후 `PreferencesKey.ROUTINES` 재수화 + reset-only DataStore key 부재
- `ReceiverRuntimeIntegrationTest`: alarm/notification/reschedule contract
- `EmergencyUnlockExpiryIntegrationTest`: 긴급해제 만료 state cleanup + 재차단 대상 결정
- `KeepMessagingServiceIntegrationTest`: stale FCM token overwrite

## 7. Release 전 최소 QA 게이트

release PR 또는 internal 배포 전에는 아래를 모두 체크한다.

- [ ] `Branch Hygiene`
- [ ] `Version Guard`
- [ ] `Android CI`
- [ ] `Android Release Build`
- [ ] `:app:testDevDebugUnitTest` 또는 해당 PR의 focused JVM test 결과
- [ ] 가능하면 `:app:connectedDevDebugAndroidTest`, 불가하면 사유 기록
- [ ] 최소 focused automation evidence
  - [ ] `com.uiery.keep.qa.StopitReleaseSmokeTest`
  - [ ] `com.uiery.keep.qa.BackupRestoreRuntimeResetIntegrationTest`
  - [ ] `com.uiery.keep.receiver.ReceiverRuntimeIntegrationTest`
  - [ ] `com.uiery.keep.service.EmergencyUnlockExpiryIntegrationTest`
  - [ ] `com.uiery.keep.service.KeepMessagingServiceIntegrationTest`
- [ ] backup/restore 정책을 건드린 PR이면 `docs/BACKUP_RESTORE_POLICY.md` 기준으로 restore/reset evidence 기록
- [ ] 아래 수동 runtime 시나리오 evidence
  - [ ] BootReceiver
  - [ ] RoutineAlarmReceiver
  - [ ] Accessibility 차단
  - [ ] 긴급해제 만료 end-to-end foreground 복귀
  - [ ] Backup / restore 후 runtime reset

## 8. PR에 남길 검증 기록 템플릿

```md
## Runtime QA evidence
- Device/Emulator:
- Build variant:
- Commands:
  - `./gradlew :app:testDevDebugUnitTest`
  - `./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.receiver.RoutineReceiverPolicyTest" --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest"`
  - `./gradlew :app:connectedDevDebugAndroidTest` (or blocked: reason)
- Manual scenarios:
  - BootReceiver: pass/fail
  - RoutineAlarmReceiver: pass/fail
  - Accessibility blocking: pass/fail
  - Emergency unlock expiry: pass/fail
  - Backup / restore runtime reset: pass/fail
- Notes:
```

## 9. 현재 한계

- 이 문서는 수동/반수동 기준선이다.
- `BootReceiver`와 `RoutineAlarmReceiver`는 `app/src/androidTest/java/com/uiery/keep/receiver/ReceiverRuntimeIntegrationTest.kt`로 최소 재수화/재예약 contract가 자동 검증된다.
- `app/src/androidTest/java/com/uiery/keep/qa/BackupRestoreRuntimeResetIntegrationTest.kt`는 복원된 Room + 비어 있는 DataStore shape에서 reset-only state가 되살아나지 않는 baseline을 고정한다.
- 여전히 실제 cold boot와 AccessibilityService의 cross-app 차단 진입은 수동 또는 추가 automation 전략이 필요하다.
- 긴급해제 만료는 `app/src/androidTest/java/com/uiery/keep/service/EmergencyUnlockExpiryIntegrationTest.kt`로 state 정리와 재차단 대상 판정을 scriptable하게 검증하지만, 실제 third-party app foreground 전환까지 포함한 end-to-end evidence는 수동 시나리오를 함께 남기는 것이 안전하다.
- issue #27이 완전히 닫히려면 위 통합 테스트와 수동 QA 기준이 함께 유지되어야 한다.
